package com.onesley.oneclick.controller.admin;

import com.onesley.oneclick.dto.admin.SystemHealthCheckDto;
import com.onesley.oneclick.service.admin.SystemHealthCheckService;
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
 * REST controller pour {@link SystemHealthCheckDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination, mutations).
 *
 * <p>Sécurité par défaut : hasRole('admin').
 * À raffiner endpoint par endpoint quand la business logic est portée (Phase 11+).
 */
@RestController
@RequestMapping("/api/system-health-checks")
@Tag(name = "SystemHealthCheck", description = "Auto-generated controller for system_health_checks")
@PreAuthorize("hasRole('admin')")
public class SystemHealthCheckController {

    private final SystemHealthCheckService service;

    public SystemHealthCheckController(SystemHealthCheckService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<SystemHealthCheckDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une SystemHealthCheck par UUID")
    public ResponseEntity<SystemHealthCheckDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
