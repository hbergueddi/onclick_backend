package com.onesley.oneclick.core.identity;

import com.onesley.oneclick.core.identity.api.RolePermissionDtos.RolePermissionMatrixDto;
import com.onesley.oneclick.core.identity.api.RolePermissionDtos.RolePermissionUpdateDto;
import com.onesley.oneclick.core.identity.api.RolePermissionDtos.RoleSummaryDto;
import com.onesley.oneclick.core.identity.internal.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * REST controller {@code /api/roles} — page « Gérer les permissions » (RBAC).
 *
 * <p>Protégé par {@code VIEW:ROLES} (lecture) / {@code UPDATE:ROLES} (sauvegarde).
 * La structure RBAC (rôle/menu/permission) appartient au bounded context
 * identity ; ces endpoints la lisent/écrivent sans dépendre de l'adaptateur
 * Spring Security.
 */
@RestController
@RequestMapping("/api/roles")
@Tag(name = "Roles", description = "RBAC — gestion des permissions par rôle")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService service;

    @GetMapping
    @Operation(summary = "Liste des rôles + nombre de permissions")
    @PreAuthorize("hasAuthority('VIEW:ROLES')")
    public List<RoleSummaryDto> listRoles() {
        return service.listRoles();
    }

    @GetMapping("/{id}/permissions")
    @Operation(summary = "Matrice des permissions d'un rôle — arbre menus + verbes cochés")
    @PreAuthorize("hasAuthority('VIEW:ROLES')")
    public RolePermissionMatrixDto getPermissions(@PathVariable UUID id) {
        return service.getRolePermissions(id);
    }

    @PutMapping("/{id}/permissions")
    @Operation(
        summary = "Enregistre la grille de permissions d'un rôle (remplace tout)",
        description = "Reçoit, par menu, la liste des verbes cochés (la colonne ALL de l'UI " +
                      "envoie les 6 verbes). Évince ensuite le cache userDetails."
    )
    @PreAuthorize("hasAuthority('UPDATE:ROLES')")
    public RolePermissionMatrixDto savePermissions(
        @PathVariable UUID id,
        @Valid @RequestBody RolePermissionUpdateDto body
    ) {
        return service.saveRolePermissions(id, body);
    }
}
