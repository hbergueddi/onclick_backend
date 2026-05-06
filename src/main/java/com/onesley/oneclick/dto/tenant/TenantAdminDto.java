package com.onesley.oneclick.dto.tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantAdminDto(
    UUID tenantId,
    UUID userId,
    String role,
    UUID invitedBy,
    Instant createdAt
) {
}
