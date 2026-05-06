package com.onesley.oneclick.controller.marketing;

import com.onesley.oneclick.dto.marketing.ExploreFeaturedDto;
import com.onesley.oneclick.service.marketing.ExploreFeaturedService;
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
 * REST controller pour {@link ExploreFeaturedDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/explore-featureds")
@Tag(name = "ExploreFeatured", description = "Auto-generated controller for explore_featured")
public class ExploreFeaturedController {

    private final ExploreFeaturedService service;

    public ExploreFeaturedController(ExploreFeaturedService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<ExploreFeaturedDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une ExploreFeatured par UUID")
    public ResponseEntity<ExploreFeaturedDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
