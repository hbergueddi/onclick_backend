package com.onesley.oneclick.core.configuration.internal;

import com.onesley.oneclick.core.configuration.api.ConfigurationDtos.FeatureFlagTargetDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Targeting custom d'un feature flag — override par user / tenant / role.
 *
 * <p>Pattern : on évalue {@code (feature_flag.enabled, rollout_pct)} d'abord,
 * puis on cherche un override dans {@code feature_flag_targets} pour le user/tenant/role
 * courant. L'override prime sur le flag global.
 */
@Entity
@Table(
    name = "feature_flag_targets",
    uniqueConstraints = @UniqueConstraint(columnNames = {"feature_flag_id", "target_type", "target_id"})
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeatureFlagTarget {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "feature_flag_id", nullable = false, insertable = false, updatable = false)
    private UUID featureFlagId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feature_flag_id", nullable = false)
    private FeatureFlag featureFlag;

    @Column(name = "target_type", nullable = false, length = 64)
     private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "enabled", nullable = false)
    @Setter private boolean enabled = true;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    public FeatureFlagTarget(UUID id, FeatureFlag featureFlag, String targetType, UUID targetId, boolean enabled) {
        this.id = id;
        this.featureFlag = featureFlag;
        this.targetType = targetType;
        this.targetId = targetId;
        this.enabled = enabled;
    }

    /** Mapping vers le DTO public exposé hors du module. */
    public FeatureFlagTargetDto toDto() {
        return new FeatureFlagTargetDto(id, featureFlagId, targetType, targetId, enabled, createdAt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffective = o instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : o.getClass();
        Class<?> thisEffective = this instanceof HibernateProxy p ? p.getHibernateLazyInitializer().getPersistentClass() : this.getClass();
        if (thisEffective != oEffective) return false;
        FeatureFlagTarget that = (FeatureFlagTarget) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return this instanceof HibernateProxy p
            ? p.getHibernateLazyInitializer().getPersistentClass().hashCode()
            : getClass().hashCode();
    }
}
