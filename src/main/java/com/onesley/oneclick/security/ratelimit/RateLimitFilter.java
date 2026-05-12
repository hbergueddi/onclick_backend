package com.onesley.oneclick.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate-limit filter — Sprint G.6.1.
 *
 * <p>Token bucket algorithm via {@link TokenBucket} (implémentation maison
 * thread-safe). Stocke les buckets en {@link ConcurrentHashMap} key = {@code
 * "<clientId>|<pathPattern>"} pour avoir un bucket par couple (client, endpoint).
 *
 * <p>Mémoire bornée : ~150 octets par bucket. Pour 100k IPs × 7 patterns =
 * ~100 MB, suffisant pour un single instance.
 *
 * <p>Active uniquement quand {@code app.rate-limit.enabled=true} (production).
 * En dev par défaut désactivé pour faciliter Swagger UI + curl manuel.
 *
 * <p>Réponse 429 : ProblemDetails RFC 7807 + headers {@code Retry-After}
 * et {@code X-RateLimit-*}, comme le standard de facto (GitHub, Stripe).
 *
 * <p>V2 distribué : remplacer {@link TokenBucket} par un backend Redis
 * (Bucket4j-Redis ou implémentation custom via Lua script atomique).
 */
@Component
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true")
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String ERROR_TYPE = "https://api.oneclick.ma/errors/rate-limit-exceeded";

    private final RateLimitProperties props;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** Cache des buckets : key = "<clientId>|<pathPattern>". */
    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        log.info("[rate-limit] enabled — default {} req / {}",
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
        String bucketKey = clientId + "|" + pathPattern;

        // 3. Récupérer/créer le bucket pour ce couple
        final int finalCapacity = capacity;
        final Duration finalRefill = refillPeriod;
        TokenBucket bucket = buckets.computeIfAbsent(
            bucketKey, k -> new TokenBucket(finalCapacity, finalRefill)
        );

        // 4. Tenter de consumer 1 token
        TokenBucket.Probe probe = bucket.tryConsume(1);
        if (probe.consumed()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(capacity));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.remainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        // 5. Bucket vide → 429
        long secondsToWait = Math.max(1, probe.waitUntilRefill().toSeconds());

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

        objectMapper.writeValue(response.getWriter(), pd);
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

    /** Pour les tests : exposer la taille du cache de buckets. */
    public int getCachedBucketsSize() {
        return buckets.size();
    }

    /** Pour les tests : vider le cache (reset between tests). */
    public void clearBuckets() {
        buckets.clear();
    }
}
