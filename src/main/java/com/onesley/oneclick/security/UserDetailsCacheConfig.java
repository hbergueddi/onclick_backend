package com.onesley.oneclick.security;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.onesley.oneclick.core.identity.api.Menu;
import com.onesley.oneclick.core.identity.api.Permission;
import com.onesley.oneclick.core.identity.api.User;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.IOException;
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
        // Robustesse proxies Hibernate (cf. HibernateProxyModule) : une association du
        // graphe (ex: Permission.menu / Menu.parent) peut revenir en Menu$HibernateProxy
        // au lieu d'une entité concrète selon l'ordre de chargement Hibernate. Sans ce
        // module, Jackson tente de sérialiser le champ synthétique $$_hibernate_interceptor
        // (ByteBuddyInterceptor, bean vide) → InvalidDefinitionException → 401. On déproxyfie
        // à la sérialisation (entité réelle déjà initialisée en session par le service).
        om.registerModule(new HibernateProxyModule());

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

    // ─── Robustesse proxies Hibernate (scoped à ce serializer) ────────────────

    /**
     * Module Jackson minimal qui sérialise tout {@link HibernateProxy} comme son entité
     * concrète sous-jacente (déproxyfication via {@link Hibernate#unproxy(Object)}), au lieu de
     * tenter de sérialiser le champ synthétique {@code $$_hibernate_interceptor} (qui mène à un
     * bean vide {@code ByteBuddyInterceptor} → {@code InvalidDefinitionException}).
     *
     * <p>Équivalent léger de {@code jackson-datatype-hibernate} (non présent au classpath) pour
     * le SEUL besoin de ce cache : le graphe {@code role→permissions→menu/action} est déjà
     * initialisé en session ({@code OneClickUserDetailsService}), donc {@code unproxy} ne déclenche
     * aucune requête lazy hors session — il retourne l'entité réelle déjà chargée. Scoped à ce
     * serializer ({@code ObjectMapper} local) → zéro impact ailleurs.</p>
     */
    static final class HibernateProxyModule extends SimpleModule {
        HibernateProxyModule() {
            addSerializer(HibernateProxy.class, new HibernateProxySerializer());
        }
    }

    /** Sérialise un {@link HibernateProxy} comme son entité concrète (déproxyfiée). */
    static final class HibernateProxySerializer extends StdSerializer<HibernateProxy> {
        HibernateProxySerializer() {
            super(HibernateProxy.class);
        }

        @Override
        public void serialize(HibernateProxy value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
            Object target = Hibernate.unproxy(value);
            if (target == null) {
                gen.writeNull();
                return;
            }
            // Re-sérialise l'entité concrète avec le serializer/mixins déclarés du mapper.
            provider.defaultSerializeValue(target, gen);
        }
    }
}
