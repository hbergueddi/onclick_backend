package com.onesley.oneclick.modules.promotion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC + flux {@code offer_reads} (V63) sur la stack de sécurité réelle
 * (filter chain → JwtDecoder → UserRoleAuthoritiesConverter → @PreAuthorize →
 * OfferReadService → repo → DB).
 *
 * <p><b>Contrat RBAC</b> : le suivi lu/non-lu réutilise {@code VIEW:OFFERS} (que le
 * CLIENT détient déjà — V29, catalogue public). Pas de nouvelle autorité. Le verrou
 * est l'ABAC ({@code OfferReadService} : tout porte sur {@code currentUser}). On
 * couvre :
 * <ul>
 *   <li>CLIENT marque lu → 201 ; liste des reads → 200 ;</li>
 *   <li>token valide MAIS sans {@code VIEW:OFFERS} (sub inconnu → 0 autorité) → 403 ;</li>
 *   <li>sans JWT → 401 ; offre inconnue → 404 ;</li>
 *   <li>idempotence : 2e marquage → 201 sans doublon en DB (UNIQUE user+offer) ;</li>
 *   <li>isolation : le read du client A n'apparaît PAS dans la liste du client B.</li>
 * </ul>
 */
class OfferReadControllerRbacIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    /** (id, bearer) d'un user réel. */
    private record RoleUser(UUID id, String bearer) {}

    /** N-ième CLIENT distinct (ordre stable) → permet 2 clients différents pour l'isolation. */
    private RoleUser clientAt(int offset) {
        String id = jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL ORDER BY u.id LIMIT 1 OFFSET ?",
            String.class, offset);
        UUID uid = UUID.fromString(id);
        return new RoleUser(uid, jwtIssuer.issueAccessToken(uid, "CLIENT").token());
    }

    /**
     * JWT signé valide mais dont le {@code sub} ne correspond à aucun user (random) :
     * le converter renvoie un token SANS autorités → 403 sur tout endpoint protégé.
     * C'est le moyen canonique de tester « authentifié mais sans VIEW:OFFERS » ici,
     * tous les rôles seedés possédant VIEW:OFFERS.
     */
    private String noAuthorityBearer() {
        return jwtIssuer.issueAccessToken(UUID.randomUUID(), "CLIENT").token();
    }

    private UUID createdOfferId;

    @BeforeEach
    void createOffer() {
        String restaurantId = jdbc.queryForObject(
            "SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class);
        Map<String, Object> create = Map.of(
            "restaurantId", restaurantId,
            "title", "Offer-reads-IT-" + UUID.randomUUID(),
            "startsAt", Instant.now().toString(),
            "expiresAt", Instant.now().plus(10, ChronoUnit.DAYS).toString());
        ResponseEntity<String> post = restTemplate.exchange(
            url("/api/offers"), HttpMethod.POST, jsonJwtEntity(create, adminBearer()), String.class);
        assertThat(post.getStatusCode().value()).isEqualTo(201);
        try {
            createdOfferId = UUID.fromString(om.readTree(post.getBody()).get("id").asText());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void cleanup() {
        if (createdOfferId != null) {
            jdbc.update("DELETE FROM offer_reads WHERE offer_id = ?", createdOfferId);
            jdbc.update("DELETE FROM offers WHERE id = ?", createdOfferId);
            createdOfferId = null;
        }
    }

    private int post(String path, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.POST, jwtEntity(jwt), String.class)
            .getStatusCode().value();
    }

    private int get(String path, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.GET, jwtEntity(jwt), String.class)
            .getStatusCode().value();
    }

    // ─── markRead : CLIENT → 201 ───────────────────────────────────────────────

    @Test
    void markRead_asClient_returns201() {
        RoleUser c = clientAt(0);
        assertThat(post("/api/offers/" + createdOfferId + "/read", c.bearer())).isEqualTo(201);
    }

    // ─── markRead : token sans VIEW:OFFERS → 403 ──────────────────────────────

    @Test
    void markRead_withoutAuthority_returns403() {
        assertThat(post("/api/offers/" + createdOfferId + "/read", noAuthorityBearer())).isEqualTo(403);
    }

    @Test
    void readsList_withoutAuthority_returns403() {
        assertThat(get("/api/offers/reads", noAuthorityBearer())).isEqualTo(403);
    }

    // ─── sans JWT → 401 ────────────────────────────────────────────────────────

    @Test
    void markRead_noJwt_returns401() {
        assertThat(post("/api/offers/" + createdOfferId + "/read", null)).isEqualTo(401);
    }

    @Test
    void readsList_noJwt_returns401() {
        assertThat(get("/api/offers/reads", null)).isEqualTo(401);
    }

    // ─── offre inconnue → 404 ──────────────────────────────────────────────────

    @Test
    void markRead_unknownOffer_returns404() {
        RoleUser c = clientAt(0);
        assertThat(post("/api/offers/" + UUID.randomUUID() + "/read", c.bearer())).isEqualTo(404);
    }

    // ─── idempotence : 2e marquage → 201, 1 seule ligne en DB ─────────────────

    @Test
    void markRead_idempotent_secondCall201_noDuplicateRow() {
        RoleUser c = clientAt(0);
        assertThat(post("/api/offers/" + createdOfferId + "/read", c.bearer())).isEqualTo(201);
        assertThat(post("/api/offers/" + createdOfferId + "/read", c.bearer())).isEqualTo(201);

        Integer rows = jdbc.queryForObject(
            "SELECT count(*) FROM offer_reads WHERE user_id = ? AND offer_id = ?",
            Integer.class, c.id(), createdOfferId);
        assertThat(rows).as("UNIQUE(user_id, offer_id) → upsert idempotent").isEqualTo(1);
    }

    // ─── flux : mark read → apparaît dans GET /reads de ce user ───────────────

    @Test
    void flow_markRead_thenAppearsInReadsList() throws Exception {
        RoleUser c = clientAt(0);
        assertThat(post("/api/offers/" + createdOfferId + "/read", c.bearer())).isEqualTo(201);

        ResponseEntity<String> reads = restTemplate.exchange(
            url("/api/offers/reads"), HttpMethod.GET, jwtEntity(c.bearer()), String.class);
        assertThat(reads.getStatusCode().value()).isEqualTo(200);
        JsonNode arr = om.readTree(reads.getBody());
        assertThat(arr.isArray()).isTrue();
        boolean found = false;
        for (JsonNode n : arr) {
            if (n.asText().equals(createdOfferId.toString())) { found = true; break; }
        }
        assertThat(found).as("l'offre marquée lue doit apparaître dans /reads de ce user").isTrue();
    }

    // ─── isolation : le read du client A ne fuite PAS dans la liste du client B ─

    @Test
    void isolation_clientB_doesNotSeeClientAReads() throws Exception {
        RoleUser a = clientAt(0);
        RoleUser b = clientAt(1);

        // A marque l'offre lue.
        assertThat(post("/api/offers/" + createdOfferId + "/read", a.bearer())).isEqualTo(201);

        // B liste ses reads : l'offre de A ne doit PAS apparaître (scope self).
        ResponseEntity<String> bReads = restTemplate.exchange(
            url("/api/offers/reads"), HttpMethod.GET, jwtEntity(b.bearer()), String.class);
        assertThat(bReads.getStatusCode().value()).isEqualTo(200);
        assertThat(bReads.getBody())
            .as("isolation ABAC : B ne voit pas le read de A")
            .doesNotContain(createdOfferId.toString());

        // Contrôle : A, lui, voit bien son read.
        ResponseEntity<String> aReads = restTemplate.exchange(
            url("/api/offers/reads"), HttpMethod.GET, jwtEntity(a.bearer()), String.class);
        assertThat(aReads.getBody()).contains(createdOfferId.toString());
    }
}
