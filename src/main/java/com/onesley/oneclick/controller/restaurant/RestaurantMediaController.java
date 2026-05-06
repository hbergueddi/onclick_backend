package com.onesley.oneclick.controller.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantMediaDto;
import com.onesley.oneclick.service.restaurant.RestaurantMediaService;
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
 * REST controller pour {@link RestaurantMediaDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination, mutations).
 *
 * <p>Sécurité par défaut : hasAnyRole('admin','restaurateur','client').
 * À raffiner endpoint par endpoint quand la business logic est portée (Phase 11+).
 */
@RestController
@RequestMapping("/api/restaurant-medias")
@Tag(name = "RestaurantMedia", description = "Auto-generated controller for restaurant_media")
@PreAuthorize("hasAnyRole('admin','restaurateur','client')")
public class RestaurantMediaController {

    private final RestaurantMediaService service;

    public RestaurantMediaController(RestaurantMediaService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<RestaurantMediaDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une RestaurantMedia par UUID")
    public ResponseEntity<RestaurantMediaDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
