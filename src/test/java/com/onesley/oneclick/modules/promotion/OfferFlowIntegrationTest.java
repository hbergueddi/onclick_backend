package com.onesley.oneclick.modules.promotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L4 « profondeur » — flux end-to-end {@code /api/offers} (HTTP réel → DB).
 *
 * <p>Couvre le cycle de vie complet (create → get → list → patch → search → delete →
 * 404 après delete) avec assertions sur le corps, plus les chemins d'erreur
 * (404 inconnu, 400 payload invalide, 403 rôle insuffisant).
 */
class OfferFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private String restaurantId() {
        return jdbc.queryForObject(
            "SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class);
    }

    @Test
    void offer_fullLifecycle_create_get_list_patch_search_delete() throws Exception {
        String admin = adminBearer();
        Map<String, Object> create = Map.of(
            "restaurantId", restaurantId(),
            "title", "L4 Flow Offer",
            "description", "offre de test L4",
            "startsAt", Instant.now().toString(),
            "expiresAt", Instant.now().plus(30, ChronoUnit.DAYS).toString(),
            "discountPct", 15,
            "type", "promo",
            "pts", 50
        );

        // CREATE → 201 + corps
        ResponseEntity<String> post = restTemplate.exchange(
            url("/api/offers"), HttpMethod.POST, jsonJwtEntity(create, admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = om.readTree(post.getBody());
        String id = created.get("id").asText();
        assertThat(created.get("title").asText()).isEqualTo("L4 Flow Offer");
        assertThat(created.get("enabled").asBoolean()).isTrue();
        assertThat(created.get("type").asText()).isEqualTo("promo");

        // GET /{id} → 200 + corps cohérent
        ResponseEntity<String> get = restTemplate.exchange(
            url("/api/offers/" + id), HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(get.getBody()).get("title").asText()).isEqualTo("L4 Flow Offer");

        // LIST filtré restaurantId → 200
        ResponseEntity<String> list = restTemplate.exchange(
            url("/api/offers?restaurantId=" + restaurantId() + "&page=0&size=5"),
            HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(list.getBody()).has("content")).isTrue();

        // PATCH → 200 + champs modifiés
        ResponseEntity<String> patch = restTemplate.exchange(
            url("/api/offers/" + id), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("title", "L4 Patched", "enabled", false), admin), String.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode patched = om.readTree(patch.getBody());
        assertThat(patched.get("title").asText()).isEqualTo("L4 Patched");
        assertThat(patched.get("enabled").asBoolean()).isFalse();

        // SEARCH (POST /search) → 200
        ResponseEntity<String> search = restTemplate.exchange(
            url("/api/offers/search"), HttpMethod.POST,
            jsonJwtEntity(Map.of("criteria", List.of(), "page", 0, "size", 5), admin), String.class);
        assertThat(search.getStatusCode()).isEqualTo(HttpStatus.OK);

        // DELETE → 204
        ResponseEntity<String> del = restTemplate.exchange(
            url("/api/offers/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class);
        assertThat(del.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // GET après delete → 404
        ResponseEntity<String> after = restTemplate.exchange(
            url("/api/offers/" + id), HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getById_unknown_returns404() {
        ResponseEntity<String> r = restTemplate.exchange(
            url("/api/offers/" + java.util.UUID.randomUUID()), HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_invalidBody_returns400() {
        // title manquant + restaurantId manquant → violation @NotNull/@NotBlank
        ResponseEntity<String> r = restTemplate.exchange(
            url("/api/offers"), HttpMethod.POST,
            jsonJwtEntity(Map.of("description", "sans titre"), adminBearer()), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_asClient_returns403() {
        Map<String, Object> create = Map.of(
            "restaurantId", restaurantId(),
            "title", "Interdit",
            "startsAt", Instant.now().toString(),
            "expiresAt", Instant.now().plus(1, ChronoUnit.DAYS).toString());
        ResponseEntity<String> r = restTemplate.exchange(
            url("/api/offers"), HttpMethod.POST, jsonJwtEntity(create, bearerForRole("CLIENT")), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void noBearer_returns401() {
        ResponseEntity<String> r = restTemplate.exchange(
            url("/api/offers?page=0&size=5"), HttpMethod.GET, jwtEntity(null), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── Gap 2 — campaign_id (regroupement de campagne multi-restaurant) ──────

    @Test
    void offer_campaignId_roundTrip() throws Exception {
        String admin = adminBearer();
        String campaignId = java.util.UUID.randomUUID().toString();
        Map<String, Object> create = Map.of(
            "restaurantId", restaurantId(),
            "title", "Campagne multi-resto",
            "startsAt", Instant.now().toString(),
            "expiresAt", Instant.now().plus(10, ChronoUnit.DAYS).toString(),
            "campaignId", campaignId);

        ResponseEntity<String> post = restTemplate.exchange(
            url("/api/offers"), HttpMethod.POST, jsonJwtEntity(create, admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = om.readTree(post.getBody());
        assertThat(created.get("campaignId").asText()).isEqualTo(campaignId);

        // GET confirme la persistance en DB (V53)
        String id = created.get("id").asText();
        ResponseEntity<String> get = restTemplate.exchange(
            url("/api/offers/" + id), HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(om.readTree(get.getBody()).get("campaignId").asText()).isEqualTo(campaignId);
    }

    // ─── Gap 1 — impressions (tracking vues offres, V21) ──────────────────────

    @Test
    void impressions_record_then_listContainsOffer() throws Exception {
        String admin = adminBearer();
        Map<String, Object> create = Map.of(
            "restaurantId", restaurantId(),
            "title", "Offre tracée",
            "startsAt", Instant.now().toString(),
            "expiresAt", Instant.now().plus(5, ChronoUnit.DAYS).toString());
        ResponseEntity<String> post = restTemplate.exchange(
            url("/api/offers"), HttpMethod.POST, jsonJwtEntity(create, admin), String.class);
        String id = om.readTree(post.getBody()).get("id").asText();

        // POST impression → 201 (VIEW:OFFERS — le client/visiteur qui voit l'offre)
        ResponseEntity<String> imp = restTemplate.exchange(
            url("/api/offers/" + id + "/impressions"), HttpMethod.POST,
            jsonJwtEntity(Map.of("type", "view"), admin), String.class);
        assertThat(imp.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // GET stats impressions → 200 (VIEW:ANALYTICS — admin)
        ResponseEntity<String> list = restTemplate.exchange(
            url("/api/offers/impressions?sinceDays=1"), HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode arr = om.readTree(list.getBody());
        assertThat(arr.isArray()).isTrue();
        boolean found = false;
        for (JsonNode n : arr) {
            if (n.get("offerId").asText().equals(id)) { found = true; break; }
        }
        assertThat(found).as("l'impression enregistrée doit apparaître dans les stats").isTrue();
    }

    @Test
    void impressions_record_unknownOffer_returns404() {
        ResponseEntity<String> imp = restTemplate.exchange(
            url("/api/offers/" + java.util.UUID.randomUUID() + "/impressions"), HttpMethod.POST,
            jsonJwtEntity(Map.of("type", "view"), adminBearer()), String.class);
        assertThat(imp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void impressions_list_asClient_returns403() {
        // VIEW:ANALYTICS est admin-only → un client ne lit pas les stats d'impressions
        ResponseEntity<String> r = restTemplate.exchange(
            url("/api/offers/impressions"), HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
