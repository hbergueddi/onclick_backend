package com.onesley.oneclick.entity.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité {@code public.tenant_features} (générée par scripts/scaffold-jpa.mjs).
 *
 * <p>Pattern : PK composite via @IdClass.
 */
@Entity
@Table(name = "tenant_features")
@IdClass(TenantFeatureId.class)
public class TenantFeature {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(name = "feature_key", nullable = false)
    private String featureKey;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TenantFeature() {
        // JPA
    }

    public UUID getTenantId() { return tenantId; }
    public String getFeatureKey() { return featureKey; }
    public Boolean getEnabled() { return enabled; }
    public Instant getUpdatedAt() { return updatedAt; }
}
