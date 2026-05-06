package com.onesley.oneclick.dto.tenant;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record TenantDto(
    UUID id,
    String slug,
    String name,
    String legalName,
    String status,
    UUID companySettingsId,
    UUID createdBy,
    Map<String, Object> features,
    Instant createdAt,
    Instant updatedAt
) {
}
