package com.onesley.oneclick.entity.tenant;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Clé composite pour {@link TenantFeature} (PK = tenant_id + feature_key).
 * Généré par scripts/scaffold-jpa.mjs.
 */
public class TenantFeatureId implements Serializable {

    private UUID tenantId;
    private String featureKey;

    public TenantFeatureId() {
        // JPA
    }

    public TenantFeatureId(UUID tenantId, String featureKey) {
        this.tenantId = tenantId;
        this.featureKey = featureKey;
    }

    public UUID getTenantId() { return tenantId; }
    public String getFeatureKey() { return featureKey; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TenantFeatureId other)) return false;
        return Objects.equals(tenantId, other.tenantId)
            && Objects.equals(featureKey, other.featureKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, featureKey);
    }
}
