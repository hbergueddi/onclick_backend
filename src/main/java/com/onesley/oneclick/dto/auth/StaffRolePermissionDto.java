package com.onesley.oneclick.dto.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code staff_role_permissions} (généré par scripts/scaffold-jpa.mjs).
 */
public record StaffRolePermissionDto(
    UUID id,
    String staffRole,
    String permissionId,
    Boolean enabled,
    Instant updatedAt,
    UUID updatedBy
) {
}
