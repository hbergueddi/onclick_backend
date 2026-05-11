package com.onesley.oneclick.core.configuration;

import com.onesley.oneclick.audit.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;

/**
 * Config TTL et taille pour les caches (Redis ou JVM in-memory).
 * Gérable depuis l'admin sans redéploiement.
 */
@Entity
@Table(name = "cache_configurations")
public class CacheConfiguration extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "cache_name", nullable = false, unique = true)
    private String cacheName;

    @Min(1)
    @Column(name = "ttl_seconds", nullable = false)
    private Integer ttlSeconds;

    @Column(name = "max_entries")
    private Integer maxEntries;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    protected CacheConfiguration() {
        // JPA
    }

    public CacheConfiguration(UUID id, String cacheName, Integer ttlSeconds) {
        this.id = id;
        this.cacheName = cacheName;
        this.ttlSeconds = ttlSeconds;
    }

    public UUID getId() { return id; }
    public String getCacheName() { return cacheName; }
    public Integer getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(Integer ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    public Integer getMaxEntries() { return maxEntries; }
    public void setMaxEntries(Integer maxEntries) { this.maxEntries = maxEntries; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

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
