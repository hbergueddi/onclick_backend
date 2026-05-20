package com.onesley.oneclick.core.identity.api;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * DTOs de la page « Gérer les permissions » (RBAC).
 *
 * <p>Le bounded context identity sert l'arbre des menus + la matrice des verbes
 * cochés pour un rôle, et reçoit la grille sauvegardée. Verbes :
 * CREATE / VIEW / UPDATE / DELETE / UPLOAD / DOWNLOAD (la colonne « ALL » de
 * l'UI = les 6 verbes, gérée côté front).
 */
public final class RolePermissionDtos {

    private RolePermissionDtos() {}

    /** {@code GET /api/roles} — un rôle + son nombre de permissions. */
    public record RoleSummaryDto(UUID id, String code, String name, long permissionCount) {}

    /** {@code GET /api/roles/{id}/permissions} — arbre récursif des menus + verbes cochés. */
    public record RolePermissionMatrixDto(
        UUID roleId,
        String roleCode,
        String roleName,
        List<String> actions,
        List<MenuNode> menus
    ) {
        public record MenuNode(
            UUID id,
            String code,
            String name,
            String icon,
            String path,
            Integer sortOrder,
            List<String> grantedActions,
            List<MenuNode> children
        ) {}
    }

    /** {@code PUT /api/roles/{id}/permissions} — la grille à enregistrer (remplace tout). */
    public record RolePermissionUpdateDto(
        @NotNull List<MenuGrant> menus
    ) {
        public record MenuGrant(
            @NotNull UUID menuId,
            List<String> actions
        ) {}
    }
}
