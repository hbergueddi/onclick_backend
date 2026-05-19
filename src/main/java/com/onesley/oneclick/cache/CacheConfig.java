package com.onesley.oneclick.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Configuration Spring Cache avec Redis comme backend.
 *
 * <p>Active {@link EnableCaching} et déclare un {@link RedisCacheManager} avec
 * des TTL différenciés par cache. Les hot paths utilisent {@code @Cacheable} :
 *
 * <ul>
 *   <li>{@code users-by-email}  — lookup auth, TTL 5 min</li>
 *   <li>{@code tenants-by-slug} — multi-tenant routing, TTL 30 min</li>
 *   <li>{@code restaurants}     — fiche détail (lecture fréquente), TTL 10 min</li>
 *   <li>{@code loyalty-tiers}   — table de référence, TTL 1 h</li>
 *   <li>{@code feature-flags}   — toggles, TTL 5 min</li>
 *   <li>{@code userDetails}     — Bug 34, payload Spring Security par user
 *       (role + authorities VERB:RESOURCE), TTL 1 h, eviction ciblée via
 *       {@code OneClickUserDetailsService.evictUser(uuid)}</li>
 * </ul>
 *
 * <p>Sérialisation JSON via Jackson (lisible dans redis-cli, debug facilité)
 * avec support des {@code java.time} (Instant, LocalDate) via JavaTimeModule.
 *
 * <p>Activé uniquement quand un {@link RedisConnectionFactory} est disponible
 * (donc tous les profils dev/enterprise/prod qui ont Redis up).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_USERS_BY_EMAIL  = "users-by-email";
    public static final String CACHE_TENANTS_BY_SLUG = "tenants-by-slug";
    public static final String CACHE_RESTAURANTS     = "restaurants";
    public static final String CACHE_LOYALTY_TIERS   = "loyalty-tiers";
    public static final String CACHE_FEATURE_FLAGS   = "feature-flags";
    /**
     * Bug 34 — Cache du payload Spring Security ({@code OneClickUserDetails})
     * keyed sur l'UUID string du user. TTL 1 h, évincé manuellement sur les
     * mutations auth-critiques (rôle, password, soft-delete) via
     * {@code OneClickUserDetailsService.evictUser(uuid)}.
     */
    public static final String CACHE_USER_DETAILS    = "userDetails";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        // Pattern Spring Data Redis 4+ :
        //  1. on instancie le serializer (ObjectMapper interne avec default typing OK)
        //  2. on enrichit via configure() pour ajouter JavaTimeModule
        //     (sinon SerializationException sur Instant des DTO records)
        var jsonSerializer = new GenericJackson2JsonRedisSerializer();
        jsonSerializer.configure(om -> om.registerModule(new JavaTimeModule()));

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        Map<String, RedisCacheConfiguration> perCache = Map.of(
            CACHE_USERS_BY_EMAIL,  base.entryTtl(Duration.ofMinutes(5)),
            CACHE_TENANTS_BY_SLUG, base.entryTtl(Duration.ofMinutes(30)),
            CACHE_RESTAURANTS,     base.entryTtl(Duration.ofMinutes(10)),
            CACHE_LOYALTY_TIERS,   base.entryTtl(Duration.ofHours(1)),
            CACHE_FEATURE_FLAGS,   base.entryTtl(Duration.ofMinutes(5)),
            CACHE_USER_DETAILS,    base.entryTtl(Duration.ofHours(1))
        );

        return RedisCacheManager.builder(cf)
            .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
            .withInitialCacheConfigurations(perCache)
            .transactionAware()
            .build();
    }
}
