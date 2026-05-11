package com.onesley.oneclick.core.tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantDto(
    UUID id,
    String name,
    String slug,
    String status,
    Instant createdAt
) {
    public static TenantDto from(Tenant t) {
        return new TenantDto(t.getId(), t.getName(), t.getSlug(), t.getStatus(), t.getCreatedAt());
    }
}
