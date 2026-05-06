package com.onesley.oneclick.dto.auth;

import com.onesley.oneclick.entity.shared.AppRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * DTO d'écriture pour POST /api/user-roles — payload d'attribution de rôle.
 *
 * <p>L'ID de l'entité est généré DB-side (default {@code gen_random_uuid()}),
 * donc absent du payload.
 */
public record UserRoleCreateDto(
    @NotNull UUID userId,
    @NotNull AppRole role
) {
}
