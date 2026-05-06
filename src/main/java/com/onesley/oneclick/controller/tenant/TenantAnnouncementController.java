package com.onesley.oneclick.controller.tenant;

import com.onesley.oneclick.dto.tenant.TenantAnnouncementDto;
import com.onesley.oneclick.service.tenant.TenantAnnouncementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link TenantAnnouncementDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination, mutations).
 *
 * <p>Sécurité par défaut : hasRole('admin').
 * À raffiner endpoint par endpoint quand la business logic est portée (Phase 11+).
 */
@RestController
@RequestMapping("/api/tenant-announcements")
@Tag(name = "TenantAnnouncement", description = "Auto-generated controller for tenant_announcements")
@PreAuthorize("hasRole('admin')")
public class TenantAnnouncementController {

    private final TenantAnnouncementService service;

    public TenantAnnouncementController(TenantAnnouncementService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<TenantAnnouncementDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une TenantAnnouncement par UUID")
    public ResponseEntity<TenantAnnouncementDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
