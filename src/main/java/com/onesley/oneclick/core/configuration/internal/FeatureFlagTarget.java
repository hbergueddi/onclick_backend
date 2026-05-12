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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

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
public class FeatureFlagTarget {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "feature_flag_id", nullable = false, insertable = false, updatable = false)
    private UUID featureFlagId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feature_flag_id", nullable = false)
    private FeatureFlag featureFlag;

    @NotBlank
    @Pattern(regexp = "^(user|tenant|role)$")
    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    protected FeatureFlagTarget() {
        // JPA
    }

    public FeatureFlagTarget(UUID id, FeatureFlag featureFlag, String targetType, UUID targetId, boolean enabled) {
        this.id = id;
        this.featureFlag = featureFlag;
        this.targetType = targetType;
        this.targetId = targetId;
        this.enabled = enabled;
    }

    public UUID getId() { return id; }
    public UUID getFeatureFlagId() { return featureFlagId; }
    public FeatureFlag getFeatureFlag() { return featureFlag; }
    public String getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }

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
