package com.onesley.oneclick.dto.tenant;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code tenant_features} (généré par scripts/scaffold-jpa.mjs).
 */
public record TenantFeatureDto(
    UUID tenantId,
    String featureKey,
    Boolean enabled,
    Instant updatedAt
) {
}
