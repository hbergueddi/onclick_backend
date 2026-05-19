package com.onesley.oneclick.core.configuration.internal;

import com.onesley.oneclick.audit.TimestampedEntity;
import com.onesley.oneclick.core.configuration.api.ConfigurationDtos.FeatureFlagDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.proxy.HibernateProxy;

import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Feature flag dynamique — peut être activé/désactivé sans redéploiement.
 *
 * <p>{@code rollout_pct} (0-100) permet un déploiement progressif sur un % de users.
 */
@Entity
@Table(name = "feature_flags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeatureFlag extends TimestampedEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @NotBlank
    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @NotBlank
    @Column(name = "name", nullable = false)
    @Setter private String name;

    @Column(name = "description")
    @Setter private String description;

    @Column(name = "enabled", nullable = false)
    @Setter private boolean enabled = false;

    @Min(0)
    @Max(100)
    @Column(name = "rollout_pct", nullable = false)
    @Setter private Integer rolloutPct = 0;

    public FeatureFlag(UUID id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public FeatureFlagDto toDto() {
        return new FeatureFlagDto(id, code, name, description, enabled, rolloutPct, getCreatedAt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        FeatureFlag that = (FeatureFlag) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
