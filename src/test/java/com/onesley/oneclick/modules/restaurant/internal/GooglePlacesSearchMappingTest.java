package com.onesley.oneclick.modules.restaurant.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires BE-4 — autocomplétion Google Places (mapping + stub-safety).
 *
 * <p>Sans contexte Spring : {@code apiKey} reste null (@Value non injecté) → {@code searchSuggestions}
 * passe en mode stub (liste vide, aucun appel HTTP). Les helpers de mapping ({@code extractCity},
 * {@code extractCuisineFromTypes}, package-private) sont testés sur des nœuds JSON façon Google v1.
 */
class GooglePlacesSearchMappingTest {

    private final ObjectMapper om = new ObjectMapper();
    private final GooglePlacesEnrichmentService svc = new GooglePlacesEnrichmentService(RestClient.builder());

    @Test
    void searchSuggestions_stubWhenNoKey_andGuards_returnEmpty() {
        assertThat(svc.searchSuggestions("Bistrot", "Maroc", 5)).isEmpty(); // pas de clé → stub
        assertThat(svc.searchSuggestions(null, "Maroc", 5)).isEmpty();
        assertThat(svc.searchSuggestions("   ", "Maroc", 5)).isEmpty();
    }

    @Test
    void extractCity_prefersLocality_thenAdmin2_elseNull() throws Exception {
        var withLocality = om.readTree("""
            {"addressComponents":[
              {"longText":"Casablanca-Settat","types":["administrative_area_level_2"]},
              {"longText":"Casablanca","types":["locality"]}
            ]}""");
        assertThat(GooglePlacesEnrichmentService.extractCity(withLocality)).isEqualTo("Casablanca");

        var admin2Only = om.readTree("""
            {"addressComponents":[
              {"longText":"Marrakech-Safi","types":["administrative_area_level_2"]}
            ]}""");
        assertThat(GooglePlacesEnrichmentService.extractCity(admin2Only)).isEqualTo("Marrakech-Safi");

        assertThat(GooglePlacesEnrichmentService.extractCity(om.readTree("{}"))).isNull();
    }

    @Test
    void extractCuisine_mapsFirstKnownType_elseNull() throws Exception {
        var moroccan = om.readTree("{\"types\":[\"restaurant\",\"moroccan_restaurant\"]}");
        assertThat(GooglePlacesEnrichmentService.extractCuisineFromTypes(moroccan)).isEqualTo("Marocaine");

        var unknown = om.readTree("{\"types\":[\"bank\",\"atm\"]}");
        assertThat(GooglePlacesEnrichmentService.extractCuisineFromTypes(unknown)).isNull();
    }
}
