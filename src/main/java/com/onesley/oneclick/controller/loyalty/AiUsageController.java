package com.onesley.oneclick.controller.loyalty;

import com.onesley.oneclick.dto.loyalty.AiUsageDto;
import com.onesley.oneclick.service.loyalty.AiUsageService;
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
 * REST controller pour {@link AiUsageDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination, mutations).
 *
 * <p>Sécurité par défaut : hasAnyRole('admin','restaurateur','client').
 * À raffiner endpoint par endpoint quand la business logic est portée (Phase 11+).
 */
@RestController
@RequestMapping("/api/ai-usages")
@Tag(name = "AiUsage", description = "Auto-generated controller for ai_usage")
@PreAuthorize("hasAnyRole('admin','restaurateur','client')")
public class AiUsageController {

    private final AiUsageService service;

    public AiUsageController(AiUsageService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<AiUsageDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une AiUsage par UUID")
    public ResponseEntity<AiUsageDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
