package com.onesley.oneclick.dto.auth;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DTO pour {@code custom_roles} (généré par scripts/scaffold-jpa.mjs).
 */
public record CustomRoleDto(
    UUID id,
    String name,
    String description,
    Map<String, Object> permissions,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt,
    UUID modifiedBy
) {
}
