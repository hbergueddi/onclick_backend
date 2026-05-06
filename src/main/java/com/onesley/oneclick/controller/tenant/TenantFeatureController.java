package com.onesley.oneclick.controller.tenant;

import com.onesley.oneclick.dto.tenant.TenantFeatureDto;
import com.onesley.oneclick.service.tenant.TenantFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller pour {@link TenantFeatureDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination, mutations).
 *
 * <p>Sécurité par défaut : hasRole('admin').
 * À raffiner endpoint par endpoint quand la business logic est portée (Phase 11+).
 */
@RestController
@RequestMapping("/api/tenant-features")
@Tag(name = "TenantFeature", description = "Auto-generated controller for tenant_features")
@PreAuthorize("hasRole('admin')")
public class TenantFeatureController {

    private final TenantFeatureService service;

    public TenantFeatureController(TenantFeatureService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<TenantFeatureDto> findAll() {
        return service.findAll();
    }
}
