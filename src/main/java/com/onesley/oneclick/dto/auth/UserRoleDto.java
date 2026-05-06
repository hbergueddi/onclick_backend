package com.onesley.oneclick.dto.auth;

import com.onesley.oneclick.entity.auth.AppRole;

import java.util.UUID;

/**
 * DTO de lecture pour {@code user_roles} — exposé via l'API REST.
 *
 * <p>Convention projet : DTOs en {@link Record}s Java 26, immutables, accessor
 * canonique généré par le compilateur.
 */
public record UserRoleDto(
    UUID id,
    UUID userId,
    AppRole role
) {
}
