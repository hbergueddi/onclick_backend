package com.onesley.oneclick.controller.admin;

import com.onesley.oneclick.dto.admin.SystemAlertDto;
import com.onesley.oneclick.service.admin.SystemAlertService;
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
 * REST controller pour {@link SystemAlertDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/system-alerts")
@Tag(name = "SystemAlert", description = "Auto-generated controller for system_alerts")
public class SystemAlertController {

    private final SystemAlertService service;

    public SystemAlertController(SystemAlertService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<SystemAlertDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une SystemAlert par UUID")
    public ResponseEntity<SystemAlertDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
