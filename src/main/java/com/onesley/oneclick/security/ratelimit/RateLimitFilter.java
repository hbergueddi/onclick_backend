package com.onesley.oneclick.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/**
 * Rate-limit filter — Sprint G.6 + Bug 35 (V2 distribué Bucket4j-Redis).
 *
 * <p>Token bucket algorithm via Bucket4j 8.x avec storage Redis Lettuce CAS
 * (cf {@link RateLimitConfig#rateLimitProxyManager}). Le {@link ProxyManager}
 * gère lui-même la concurrence (compare-and-swap atomique côté Redis) et
 * l'expiration TTL — pas de map mémoire applicative.
 *
 * <p>Clé Redis : {@code "rate-limit:<clientId>:<pathPattern>"} (lisible dans
 * {@code redis-cli KEYS 'rate-limit:*'}).
 *
 * <p>Multi-pod safe : 2 instances backend voient le même état bucket via Redis,
 * la limite est donc respectée globalement (vs. l'implémentation V1
 * {@code ConcurrentHashMap} où chaque pod avait son propre compteur → 2× la
 * limite avec 2 pods).
 *
 * <p>Active uniquement quand {@code app.rate-limit.enabled=true} (production).
 * En dev par défaut désactivé pour faciliter Swagger UI + curl manuel.
 *
 * <p>Réponse 429 : ProblemDetails RFC 7807 + headers {@code Retry-After}
 * et {@code X-RateLimit-*}, comme le standard de facto (GitHub, Stripe).
 */
@Component
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true")
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String ERROR_TYPE = "https://api.oneclick.ma/errors/rate-limit-exceeded";
    private static final String REDIS_KEY_PREFIX = "rate-limit:";

    /**
     * ObjectMapper local (convention codebase : ResendClient, GroqStreamingClient,
     * GooglePlacesEnrichmentService font idem). Le projet n'expose pas de bean
     * {@link ObjectMapper} — Spring MVC garde le sien encapsulé dans ses
     * HttpMessageConverters. {@link JavaTimeModule} pour sérialiser les éventuels
     * {@code Instant}/{@code Duration} du ProblemDetail.
     */
    private static final ObjectMapper OBJECT_MAPPER =
        new ObjectMapper().registerModule(new JavaTimeModule());

    private final RateLimitProperties props;
    private final ProxyManager<String> proxyManager;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(
        RateLimitProperties props,
        ProxyManager<String> proxyManager
    ) {
        this.props = props;
        this.proxyManager = proxyManager;
        log.info("[rate-limit] Bucket4j-Redis enabled — default {} req / {}",
            props.getDefaults().getCapacity(), props.getDefaults().getRefillPeriod());
        for (var e : props.getEndpoints()) {
            log.info("[rate-limit]   override {} → {} req / {}",
                e.getPath(), e.getCapacity(), e.getRefillPeriod());
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        // Skip non-API paths (actuator, swagger, static, etc.)
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        // 1. Résoudre le bucket config pour cet endpoint
        RateLimitProperties.EndpointConfig endpointConfig = matchEndpoint(uri);
        String pathPattern = endpointConfig != null ? endpointConfig.getPath() : "__default__";
        int capacity = endpointConfig != null
            ? endpointConfig.getCapacity()
            : props.getDefaults().getCapacity();
        Duration refillPeriod = endpointConfig != null
            ? endpointConfig.getRefillPeriod()
            : props.getDefaults().getRefillPeriod();

        // 2. Identifier le client (IP-based, X-Forwarded-For aware)
        String clientId = resolveClientId(request);
        String bucketKey = REDIS_KEY_PREFIX + clientId + ":" + pathPattern;

        // 3. Récupérer/créer le bucket distant (CAS atomique côté Redis)
        BucketConfiguration config = BucketConfiguration.builder()
            .addLimit(Bandwidth.simple(capacity, refillPeriod))
            .build();
        Bucket bucket = proxyManager.builder().build(bucketKey, () -> config);

        // 4. Tenter de consumer 1 token + récupérer le remaining
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        // 5. Bucket vide → 429
        long secondsToWait = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds());

        log.warn("[rate-limit] 429 for {} on {} (retry after {}s)", clientId, uri, secondsToWait);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(secondsToWait));
        response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setHeader("X-RateLimit-Reset",
            String.valueOf(Instant.now().plusSeconds(secondsToWait).getEpochSecond()));

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            "Trop de requêtes — réessayez dans " + secondsToWait + " secondes"
        );
        pd.setType(URI.create(ERROR_TYPE));
        pd.setTitle("Too Many Requests");
        pd.setInstance(URI.create(uri));
        pd.setProperty("timestamp", Instant.now().toString());
        pd.setProperty("traceId", UUID.randomUUID().toString());
        pd.setProperty("retryAfterSeconds", secondsToWait);
        pd.setProperty("limit", capacity);
        pd.setProperty("refillPeriod", refillPeriod.toString());

        OBJECT_MAPPER.writeValue(response.getWriter(), pd);
    }

    /** Match l'URL avec la première EndpointConfig matchant l'Ant pattern. */
    private RateLimitProperties.EndpointConfig matchEndpoint(String uri) {
        for (var ec : props.getEndpoints()) {
            if (ec.getPath() != null && pathMatcher.match(ec.getPath(), uri)) {
                return ec;
            }
        }
        return null;
    }

    /**
     * Identifie le client.
     * <ol>
     *   <li>X-Forwarded-For first hop (derrière Nginx en prod)</li>
     *   <li>Sinon : request.getRemoteAddr()</li>
     * </ol>
     *
     * <p>V1 : rate-limit par IP indépendamment de l'auth (cas anti-bruteforce
     * sur login). V2 : différencier bucket auth vs anonymous.
     */
    private String resolveClientId(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
