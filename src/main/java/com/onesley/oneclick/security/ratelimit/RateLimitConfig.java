package com.onesley.oneclick.security.ratelimit;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * Configuration Spring du rate-limiter — Sprint G.6 + Bug 35 (Bucket4j V2 distribué).
 *
 * <p>Active {@link RateLimitProperties} comme bean Spring et expose 3 beans
 * conditionnels ({@code app.rate-limit.enabled=true}) :
 *
 * <ol>
 *   <li>{@link RedisClient} — client Lettuce natif dédié rate-limit, distinct du
 *       client Spring Data Redis (codec différent, pas de conflit avec le cache
 *       Jackson {@code userDetails}).</li>
 *   <li>{@link StatefulRedisConnection} — connexion {@code <String, byte[]>} pour
 *       le proxy manager Bucket4j (clés humainement lisibles dans {@code redis-cli
 *       KEYS 'rate-limit:*'}).</li>
 *   <li>{@link ProxyManager} — wrapper Bucket4j qui crée/restaure les buckets
 *       depuis Redis via le pattern CAS (compare-and-swap). TTL Redis aligné
 *       sur la plus longue {@code refill-period} configurée pour ne jamais
 *       expirer un bucket avant son prochain refill.</li>
 * </ol>
 *
 * <p>Le {@link RateLimitFilter} est enregistré explicitement dans
 * {@code SecurityConfig.securityFilterChain} pour s'exécuter AVANT le filtre
 * JWT (sinon on consomme du CPU et des SELECT DB pour des requêtes qui auraient
 * été 429-bloquées de toute façon).
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
@Slf4j
public class RateLimitConfig {

    /**
     * Client Lettuce natif dédié au rate-limit. Distinct du
     * {@code LettuceConnectionFactory} de Spring Data Redis : on a besoin d'un
     * codec {@code (String, byte[])} alors que Spring Data fournit
     * {@code (byte[], byte[])} pour son {@code GenericJackson2JsonRedisSerializer}.
     *
     * <p>Coût : 1 connexion Redis supplémentaire (~quelques KB de RAM + 1 socket).
     * Acceptable étant donné qu'on partage la même instance physique Redis.
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true")
    public RedisClient rateLimitRedisClient(
        @Value("${spring.data.redis.host:localhost}") String host,
        @Value("${spring.data.redis.port:6379}") int port,
        @Value("${spring.data.redis.password:}") String password
    ) {
        RedisURI.Builder uri = RedisURI.Builder.redis(host, port);
        if (password != null && !password.isBlank()) {
            uri.withPassword(password.toCharArray());
        }
        log.info("[rate-limit] Lettuce client target redis://{}:{}", host, port);
        return RedisClient.create(uri.build());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true")
    public StatefulRedisConnection<String, byte[]> rateLimitRedisConnection(RedisClient client) {
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return client.connect(codec);
    }

    /**
     * Proxy manager Bucket4j Lettuce-CAS (compare-and-swap atomique côté Redis).
     *
     * <p>L'expiration strategy {@code basedOnTimeForRefillingBucketUpToMax}
     * fait expirer la clé Redis après {@code maxTtl} d'inactivité — ainsi la
     * map des buckets s'auto-nettoie sans cron applicatif (vs. le {@code
     * ConcurrentHashMap} maison qui grossissait indéfiniment).
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true")
    public ProxyManager<String> rateLimitProxyManager(
        StatefulRedisConnection<String, byte[]> connection,
        RateLimitProperties props
    ) {
        Duration maxTtl = Stream.concat(
                Stream.of(props.getDefaults().getRefillPeriod()),
                props.getEndpoints().stream().map(RateLimitProperties.EndpointConfig::getRefillPeriod)
            )
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(Duration.ofMinutes(5));

        log.info("[rate-limit] Bucket4j proxy manager — TTL Redis max {}", maxTtl);

        return LettuceBasedProxyManager.builderFor(connection)
            .withExpirationStrategy(
                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(maxTtl)
            )
            .build();
    }
}
