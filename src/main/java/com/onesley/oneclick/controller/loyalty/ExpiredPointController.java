package com.onesley.oneclick.controller.loyalty;

import com.onesley.oneclick.dto.loyalty.ExpiredPointDto;
import com.onesley.oneclick.service.loyalty.ExpiredPointService;
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
 * REST controller pour {@link ExpiredPointDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination, mutations).
 *
 * <p>Sécurité par défaut : hasAnyRole('admin','restaurateur','client').
 * À raffiner endpoint par endpoint quand la business logic est portée (Phase 11+).
 */
@RestController
@RequestMapping("/api/expired-points")
@Tag(name = "ExpiredPoint", description = "Auto-generated controller for expired_points")
@PreAuthorize("hasAnyRole('admin','restaurateur','client')")
public class ExpiredPointController {

    private final ExpiredPointService service;

    public ExpiredPointController(ExpiredPointService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<ExpiredPointDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une ExpiredPoint par UUID")
    public ResponseEntity<ExpiredPointDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
