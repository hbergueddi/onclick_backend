package com.onesley.oneclick.controller.tenant;

import com.onesley.oneclick.dto.tenant.TenantDto;
import com.onesley.oneclick.permission.PermissionAction;
import com.onesley.oneclick.permission.RequirePermission;
import com.onesley.oneclick.service.tenant.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
@Tag(name = "Tenants", description = "Multi-tenant whitelabel (oneclick, restopro, palmeraie, homu, ...)")
// Politique de groupe (Phase 5.2) — gardée pour défense en profondeur
@PreAuthorize("hasRole('admin')")
// Permission fine (Phase 6.2) — actions explicites par méthode
@RequirePermission(menu = "tenant", action = PermissionAction.READ)
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les tenants (admin only ultimately)")
    public List<TenantDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/active")
    @Operation(summary = "Liste les tenants en statut actif")
    public List<TenantDto> findAllActive() {
        return service.findAllActive();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un tenant par UUID")
    public ResponseEntity<TenantDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/by-slug/{slug}")
    @Operation(summary = "Résolution tenant par slug (whitelabel boot)")
    public ResponseEntity<TenantDto> findBySlug(@PathVariable String slug) {
        return service.findBySlug(slug)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
