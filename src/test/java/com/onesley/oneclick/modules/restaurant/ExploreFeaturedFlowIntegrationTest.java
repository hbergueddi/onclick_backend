package com.onesley.oneclick.modules.restaurant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/restaurants/featured} : list + upsert + delete (self-clean). */
class ExploreFeaturedFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String restaurantId() { return jdbc.queryForObject("SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void featured_upsertListDelete() throws Exception {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/restaurants/featured"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> post = restTemplate.exchange(url("/api/restaurants/featured"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restaurantId(), "rank", 1, "enabled", true), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();

        assertThat(restTemplate.exchange(url("/api/restaurants/featured/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void delete_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/restaurants/featured/" + UUID.randomUUID()),
            HttpMethod.DELETE, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
