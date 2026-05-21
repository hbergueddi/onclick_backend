package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/restaurants/{id}/enrich-google-places} (mode stub sans clé API). */
class RestaurantEnrichmentFlowIntegrationTest extends AbstractIntegrationTest {

    private String restaurantId() { return jdbc.queryForObject("SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void enrich_stubMode_200() {
        ResponseEntity<String> r = restTemplate.exchange(
            url("/api/restaurants/" + restaurantId() + "/enrich-google-places?force=false"),
            HttpMethod.POST, jwtEntity(adminBearer()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        // stub : pas de clé API → enriched=false / skipped
        assertThat(r.getBody()).contains("enriched");
    }

    @Test
    void enrich_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/restaurants/" + UUID.randomUUID() + "/enrich-google-places"),
            HttpMethod.POST, jwtEntity(null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
