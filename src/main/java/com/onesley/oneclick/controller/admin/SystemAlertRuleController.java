package com.onesley.oneclick.controller.admin;

import com.onesley.oneclick.dto.admin.SystemAlertRuleDto;
import com.onesley.oneclick.service.admin.SystemAlertRuleService;
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
 * REST controller pour {@link SystemAlertRuleDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/system-alert-rules")
@Tag(name = "SystemAlertRule", description = "Auto-generated controller for system_alert_rules")
public class SystemAlertRuleController {

    private final SystemAlertRuleService service;

    public SystemAlertRuleController(SystemAlertRuleService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<SystemAlertRuleDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une SystemAlertRule par UUID")
    public ResponseEntity<SystemAlertRuleDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
