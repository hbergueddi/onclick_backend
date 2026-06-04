package com.onesley.oneclick.core.identity.api;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de lecture pour User — exposé via l'API REST.
 *
 * <p>{@code passwordHash} jamais exposé (sécurité).
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code User.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).
 */
public record UserDto(
    UUID id,
    UUID tenantId,
    UUID roleId,
    String roleCode,
    String email,
    String phone,
    String firstName,
    String lastName,
    String avatarUrl,
    String city,
    String language,
    String status,
    boolean accountNonExpired,
    boolean accountNonLocked,
    boolean credentialsNonExpired,
    boolean enabled,
    Instant lastLoginAt,
    Instant createdAt,
    String referralCode
) {
}
