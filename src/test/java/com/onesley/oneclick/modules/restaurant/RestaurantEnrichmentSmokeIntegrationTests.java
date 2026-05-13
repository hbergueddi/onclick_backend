package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E Sprint I.3 — RestaurantEnrichmentController (Google Places stub safe).
 */
class RestaurantEnrichmentSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void enrichGooglePlaces_stubMode_returns200() {
        // Resto réel via /api/restaurants?size=1
        ResponseEntity<String> list = restTemplate.exchange(
            url("/api/restaurants?page=0&size=1"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Use a deterministic test resto id — la 1ère page contient au moins 1 resto seedé
        String body = list.getBody();
        int idStart = body.indexOf("\"id\":\"") + 6;
        int idEnd = body.indexOf("\"", idStart);
        String restaurantId = body.substring(idStart, idEnd);

        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId + "/enrich-google-places"),
            HttpMethod.POST, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // En stub mode (pas de api-key) → skipped=true / reason=no_api_key
        // En prod → enriched=true / placeId/rating
        assertThat(response.getBody()).containsAnyOf("skipped", "enriched");
    }

    @Test
    void exploreFeatured_public_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/restaurants/featured"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
