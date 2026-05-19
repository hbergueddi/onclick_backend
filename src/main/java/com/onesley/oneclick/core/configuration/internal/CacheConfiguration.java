package com.onesley.oneclick.core.configuration.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.configuration.api.ConfigurationDtos.CacheConfigDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Size;

/**
 * Config TTL et taille pour les caches (Redis ou JVM in-memory).
 * Gérable depuis l'admin sans redéploiement.
 */
@Entity
@Table(name = "cache_configurations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CacheConfiguration extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "cache_name", nullable = false, unique = true)
    @Size(max = 255) private String cacheName;

    @Min(1)
    @Column(name = "ttl_seconds", nullable = false)
    @Setter private Integer ttlSeconds;

    @Column(name = "max_entries")
    @Setter private Integer maxEntries;

    @Column(name = "enabled", nullable = false)
    @Setter private boolean enabled = true;

    public CacheConfiguration(UUID id, String cacheName, Integer ttlSeconds) {
        this.id = id;
        this.cacheName = cacheName;
        this.ttlSeconds = ttlSeconds;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public CacheConfigDto toDto() {
        return new CacheConfigDto(id, cacheName, ttlSeconds, maxEntries, enabled, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        CacheConfiguration that = (CacheConfiguration) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
