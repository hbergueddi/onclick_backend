package com.onesley.oneclick.dto.tenant;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code tenant_branding} (généré par scripts/scaffold-jpa.mjs).
 */
public record TenantBrandingDto(
    UUID tenantId,
    String logoUrl,
    String logoDarkUrl,
    String faviconUrl,
    String primaryColor,
    String accentColor,
    String backgroundColor,
    String tagline,
    String appNameWin,
    String appNameStore,
    String customDomain,
    Instant updatedAt
) {
}
