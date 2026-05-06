package com.onesley.oneclick.controller.support;

import com.onesley.oneclick.dto.support.ClientScoreConfigDto;
import com.onesley.oneclick.service.support.ClientScoreConfigService;
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
 * REST controller pour {@link ClientScoreConfigDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination, mutations).
 *
 * <p>Sécurité par défaut : hasAnyRole('admin','client').
 * À raffiner endpoint par endpoint quand la business logic est portée (Phase 11+).
 */
@RestController
@RequestMapping("/api/client-score-configs")
@Tag(name = "ClientScoreConfig", description = "Auto-generated controller for client_score_config")
@PreAuthorize("hasAnyRole('admin','client')")
public class ClientScoreConfigController {

    private final ClientScoreConfigService service;

    public ClientScoreConfigController(ClientScoreConfigService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<ClientScoreConfigDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une ClientScoreConfig par UUID")
    public ResponseEntity<ClientScoreConfigDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
