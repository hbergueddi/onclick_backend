package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.modules.restaurant.api.PlaceSuggestionDto;
import com.onesley.oneclick.modules.restaurant.internal.GooglePlacesEnrichmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * BE-4 (plan RESTAURANT-ONBOARDING) — autocomplétion Google Places <b>publique</b> pour le
 * formulaire d'inscription resto (candidat non authentifié).
 *
 * <p>{@code GET /api/places/search} : permitAll (cf. SecurityConfig) + rate-limité par IP
 * (cf. {@code app.rate-limit.endpoints} — anti-abus / coût Google). Réponse minimisée
 * ({@link PlaceSuggestionDto}). Stub-safe : liste vide si la clé Google n'est pas configurée.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Places", description = "BE-4 — autocomplétion Google Places (formulaire inscription resto)")
public class PlacesController {

    private final GooglePlacesEnrichmentService placesService;

    @GetMapping("/api/places/search")
    @Operation(summary = "Autocomplétion Google Places (public) — suggestions resto sanitizées")
    public List<PlaceSuggestionDto> search(
            @RequestParam("q") String query,
            @RequestParam(value = "country", required = false, defaultValue = "Maroc") String country,
            @RequestParam(value = "limit", required = false, defaultValue = "5") int limit) {
        return placesService.searchSuggestions(query, country, limit);
    }
}
