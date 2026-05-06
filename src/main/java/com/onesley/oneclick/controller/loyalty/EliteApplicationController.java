package com.onesley.oneclick.controller.loyalty;

import com.onesley.oneclick.dto.loyalty.EliteApplicationDto;
import com.onesley.oneclick.service.loyalty.EliteApplicationService;
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
 * REST controller pour {@link EliteApplicationDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/elite-applications")
@Tag(name = "EliteApplication", description = "Auto-generated controller for elite_applications")
public class EliteApplicationController {

    private final EliteApplicationService service;

    public EliteApplicationController(EliteApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<EliteApplicationDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une EliteApplication par UUID")
    public ResponseEntity<EliteApplicationDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
