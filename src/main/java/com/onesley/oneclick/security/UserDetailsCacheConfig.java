package com.onesley.oneclick.security;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.onesley.oneclick.core.identity.api.Menu;
import com.onesley.oneclick.core.identity.api.Permission;
import com.onesley.oneclick.core.identity.api.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Sérialisation dédiée du cache {@code userDetails}.
 *
 * <p>Depuis la refonte, {@link OneClickUserDetails} tient l'entité {@link User}
 * entière (« comme user »). Deux écueils à la mise en cache Redis (JSON) :
 *
 * <ol>
 *   <li><strong>Champs sans setter</strong> (id, code…) → on lit/écrit par
 *       <strong>champs</strong> ({@code FIELD} visibility).</li>
 *   <li><strong>Collections Hibernate</strong> : un serializer à <em>default
 *       typing</em> (comme le {@code GenericJackson2}) écrit le type runtime
 *       {@code @class = org.hibernate…PersistentSet}, irrécupérable à la
 *       relecture (« no session »). On utilise donc un
 *       {@link Jackson2JsonRedisSerializer} <strong>typé</strong> sur
 *       {@link OneClickUserDetails} (pas de default typing) : les collections
 *       sont sérialisées en simples tableaux selon le type déclaré
 *       ({@code Set<Permission>}) et relues en {@code Set} ordinaire.</li>
 * </ol>
 *
 * <p>MixIns (scope ce serializer uniquement → zéro impact ailleurs) :
 * <ul>
 *   <li>{@code User.passwordHash} → mot de passe <em>caché</em> ;</li>
 *   <li>{@code User.tenant} → proxy LAZY non chargé ;</li>
 *   <li>{@code Permission.role} → back-ref (casse le cycle Role↔Permission) ;</li>
 *   <li>{@code Menu.parent} → self-ref LAZY (on garde {@code parentId}).</li>
 * </ul>
 *
 * <p>Le graphe {@code role → permissions → menu/action} est initialisé en session
 * par {@code OneClickUserDetailsService} (le JOIN FETCH + un touch explicite)
 * avant la mise en cache.
 */
@Configuration
public class UserDetailsCacheConfig {

    public static final String BEAN_NAME = "userDetailsCacheConfiguration";

    @Bean(BEAN_NAME)
    public RedisCacheConfiguration userDetailsCacheConfiguration() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        // Round-trip des entités par CHAMPS uniquement (pas de setters partout).
        om.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
        om.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        om.addMixIn(User.class, UserMixin.class);
        om.addMixIn(Permission.class, PermissionMixin.class);
        om.addMixIn(Menu.class, MenuMixin.class);

        // Typé sur OneClickUserDetails (le cache ne stocke que ça) → PAS de default
        // typing → pas de @class=PersistentSet → relecture sans session OK.
        var serializer = new Jackson2JsonRedisSerializer<>(om, OneClickUserDetails.class);

        return RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            .entryTtl(Duration.ofHours(1))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }

    // ─── MixIns (scoped à ce serializer) — ignorent par NOM de champ ──────────

    abstract static class UserMixin {
        @JsonIgnore Object passwordHash;   // mot de passe caché
        @JsonIgnore Object tenant;          // proxy LAZY non chargé
    }

    abstract static class PermissionMixin {
        @JsonIgnore Object role;            // back-ref → casse le cycle
    }

    abstract static class MenuMixin {
        @JsonIgnore Object parent;          // self-ref LAZY (on garde parentId)
    }
}
