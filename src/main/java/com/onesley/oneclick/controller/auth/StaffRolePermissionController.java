package com.onesley.oneclick.controller.auth;

import com.onesley.oneclick.dto.auth.StaffRolePermissionDto;
import com.onesley.oneclick.service.auth.StaffRolePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link StaffRolePermissionDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/staff-role-permissions")
@Tag(name = "StaffRolePermission", description = "Auto-generated controller for staff_role_permissions")
public class StaffRolePermissionController {

    private final StaffRolePermissionService service;

    public StaffRolePermissionController(StaffRolePermissionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<StaffRolePermissionDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une StaffRolePermission par UUID")
    public ResponseEntity<StaffRolePermissionDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
