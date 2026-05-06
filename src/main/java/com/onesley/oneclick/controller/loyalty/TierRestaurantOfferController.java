package com.onesley.oneclick.controller.loyalty;

import com.onesley.oneclick.dto.loyalty.TierRestaurantOfferDto;
import com.onesley.oneclick.service.loyalty.TierRestaurantOfferService;
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
 * REST controller pour {@link TierRestaurantOfferDto} (généré par scripts/scaffold-jpa.mjs).
 * Endpoints minimaux — étendre selon les besoins métier (filtres, pagination,
 * mutations, sécurité @PreAuthorize).
 */
@RestController
@RequestMapping("/api/tier-restaurant-offers")
@Tag(name = "TierRestaurantOffer", description = "Auto-generated controller for tier_restaurant_offers")
public class TierRestaurantOfferController {

    private final TierRestaurantOfferService service;

    public TierRestaurantOfferController(TierRestaurantOfferService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Liste tous les éléments")
    public List<TierRestaurantOfferDto> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une TierRestaurantOffer par UUID")
    public ResponseEntity<TierRestaurantOfferDto> findById(@PathVariable UUID id) {
        return service.findById(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
