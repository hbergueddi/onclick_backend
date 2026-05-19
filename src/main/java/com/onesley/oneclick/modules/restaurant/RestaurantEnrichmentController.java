package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.modules.restaurant.internal.GooglePlacesEnrichmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Endpoint enrichissement Google Places — Sprint I.3.
 *
 * <p>Port EF {@code fetch-google-places}. ToS Google compliant (option C) :
 * pas de stockage des photos, uniquement data (rating, hours, GPS, address).
 */
@RestController
@RequestMapping("/api/restaurants")
@Tag(name = "Restaurant enrichment", description = "Sprint I.3 — Google Places enrichment")
@RequiredArgsConstructor
public class RestaurantEnrichmentController {

    private final GooglePlacesEnrichmentService service;

    @PostMapping("/{id}/enrich-google-places")
    @Operation(summary = "Enrichit un restaurant via Google Places API (rating, hours, GPS, phone, website)")
    // Bug 32 (Batch D RBAC v2) — RESOURCE=RESTAURANTS (enrichissement fiche).
    @PreAuthorize("hasAuthority('UPDATE:RESTAURANTS')")
    public Map<String, Object> enrich(
        @PathVariable UUID id,
        @RequestParam(defaultValue = "false") boolean force
    ) {
        return service.enrichRestaurant(id, force);
    }
}
