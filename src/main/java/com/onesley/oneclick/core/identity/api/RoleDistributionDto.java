package com.onesley.oneclick.core.identity.api;

/**
 * Nombre d'utilisateurs par rôle applicatif — alimente le dashboard admin
 * {@code GestionRoles} (onglet « Rôles système »).
 *
 * @param role  code du rôle ({@code CLIENT}, {@code STAFF}, {@code RESTAURATEUR},
 *              {@code GROUP_ADMIN}, {@code SUPERADMIN})
 * @param count nombre d'utilisateurs actifs portant ce rôle (soft-deletes exclus)
 */
public record RoleDistributionDto(String role, long count) {
}
