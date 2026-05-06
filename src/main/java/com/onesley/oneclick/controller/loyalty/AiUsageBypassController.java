package com.onesley.oneclick.controller.loyalty;

import com.onesley.oneclick.dto.loyalty.AiUsageBypassDto;
import com.onesley.oneclick.service.loyalty.AiUsageBypassService;
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
 * REST controller pour {@link AiUsageBypassDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/ai-usage-bypass")
@Tag(name = "AiUsageBypass", description = "Auto-generated controller for ai_usage_bypass")
public class AiUsageBypassController {

    private final AiUsageBypassService service;

    public AiUsageBypassController(AiUsageBypassService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<AiUsageBypassDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une AiUsageBypass par UUID")
    public ResponseEntity<AiUsageBypassDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
