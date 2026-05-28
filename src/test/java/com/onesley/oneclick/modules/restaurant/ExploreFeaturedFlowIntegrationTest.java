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
    void featured_adminListAll_includesDisabledAndEditorialFields() throws Exception {
        // V57 — upsert avec label/notes + enabled=false, puis :
        //   • GET /all (admin)  → inclut l'entrée désactivée + label/notes
        //   • GET     (public)  → l'exclut (enabled=true only)
        String admin = adminBearer();
        String rid = restaurantId();

        ResponseEntity<String> post = restTemplate.exchange(url("/api/restaurants/featured"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", rid, "rank", 3, "enabled", false,
                "label", "Top Chef", "notes", "note admin interne"), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        // L'upsert renvoie les champs éditoriaux persistés.
        assertThat(om.readTree(post.getBody()).get("label").asText()).isEqualTo("Top Chef");
        assertThat(om.readTree(post.getBody()).get("notes").asText()).isEqualTo("note admin interne");

        // Liste admin (tous) → inclut l'entrée désactivée + ses champs.
        ResponseEntity<String> all = restTemplate.exchange(url("/api/restaurants/featured/all"),
            HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(all.getBody()).contains(id).contains("Top Chef").contains("note admin interne");

        // Flux public (activés uniquement) → exclut l'entrée désactivée.
        ResponseEntity<String> pub = restTemplate.exchange(url("/api/restaurants/featured"),
            HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(pub.getBody()).doesNotContain(id);

        // self-clean
        restTemplate.exchange(url("/api/restaurants/featured/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void adminListAll_requiresAuth_401() {
        // GET /all est protégé (VIEW:RESTAURANTS) contrairement au flux public GET racine.
        assertThat(restTemplate.exchange(url("/api/restaurants/featured/all"),
            HttpMethod.GET, org.springframework.http.HttpEntity.EMPTY, String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void delete_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/restaurants/featured/" + UUID.randomUUID()),
            HttpMethod.DELETE, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
