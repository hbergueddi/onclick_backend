package com.onesley.oneclick.controller.loyalty;

import com.onesley.oneclick.dto.loyalty.GainRuleRequestDto;
import com.onesley.oneclick.service.loyalty.GainRuleRequestService;
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
 * REST controller pour {@link GainRuleRequestDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination, mutations).
 *
 * <p>Sécurité par défaut : hasAnyRole('admin','restaurateur','client').
 * À raffiner endpoint par endpoint quand la business logic est portée (Phase 11+).
 */
@RestController
@RequestMapping("/api/gain-rule-requests")
@Tag(name = "GainRuleRequest", description = "Auto-generated controller for gain_rule_requests")
@PreAuthorize("hasAnyRole('admin','restaurateur','client')")
public class GainRuleRequestController {

    private final GainRuleRequestService service;

    public GainRuleRequestController(GainRuleRequestService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<GainRuleRequestDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une GainRuleRequest par UUID")
    public ResponseEntity<GainRuleRequestDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
