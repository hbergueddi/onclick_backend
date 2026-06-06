package com.onesley.oneclick.modules.seminar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC/ABAC L4 — « Séminaires » (PCC, ressource {@code SEMINARS} seedée par V73).
 *
 * <p>Invariants vérifiés sur la stack réelle (filter chain → JwtDecoder → converter →
 * {@code @PreAuthorize} → service ABAC → DB) :
 * <ul>
 *   <li>Sans JWT → 401.</li>
 *   <li>Un rôle SANS {@code CREATE:SEMINARS} (RESTAURATEUR) → 403 sur POST.</li>
 *   <li>CLIENT : flow create → /mine → (CLIENT status → 403) → admin status → 200.</li>
 *   <li>Vue membre /mine : {@code notesInternal} masqué même après traitement commercial.</li>
 *   <li>GET /inbox admin → 200 (tableau).</li>
 * </ul>
 *
 * <p>Données : membres PCC seedés ({@code memberN@palmeraie.com}, CLIENT, tenant palmeraie).
 * Les demandes créées par le test sont nettoyées en teardown (pas de soft-delete).</p>
 */
class SeminarRbacIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private final java.util.List<UUID> createdIds = new java.util.ArrayList<>();

    private UUID userIdByEmail(String email) {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM users WHERE email = ?", String.class, email));
    }

    /** Bearer pour un membre PCC précis (rôle CLIENT). */
    private String bearerFor(String email) {
        return jwtIssuer.issueAccessToken(userIdByEmail(email), "CLIENT").token();
    }

    private static final String VALID_BODY =
        "{\"companyName\":\"Atlas Conseil\",\"contactName\":\"Karim Benali\","
        + "\"contactEmail\":\"karim@atlas-conseil.ma\",\"expectedAttendees\":40,"
        + "\"needsText\":\"Salle plénière 40 pers. + déjeuner\"}";

    @AfterEach
    void cleanup() {
        for (UUID id : createdIds) {
            jdbc.update("DELETE FROM seminar_requests WHERE id = ?", id);
        }
        createdIds.clear();
    }

    // ─── A. 401 sans JWT ─────────────────────────────────────────────────────────

    @Test
    void noJwt_returns401() {
        assertThat(restTemplate.exchange(url("/api/pcc/seminars/mine"),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange(url("/api/pcc/seminars"),
            HttpMethod.POST, jsonJwtEntity(VALID_BODY, null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── B. RBAC : rôle sans CREATE:SEMINARS → 403 ───────────────────────────────

    @Test
    void create_byRoleWithoutCreate_forbidden() {
        // RESTAURATEUR détient VIEW/UPDATE:SEMINARS mais PAS CREATE:SEMINARS → 403.
        ResponseEntity<String> resp = restTemplate.exchange(url("/api/pcc/seminars"),
            HttpMethod.POST, jsonJwtEntity(VALID_BODY, bearerForRole("RESTAURATEUR")), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── C. CLIENT — flow create → /mine → (client status 403) → admin status 200 ─

    @Test
    void client_create_list_status_flow() throws Exception {
        String member = bearerFor("member1@palmeraie.com");
        UUID memberId = userIdByEmail("member1@palmeraie.com");

        // 1. create → 201.
        ResponseEntity<String> createResp = restTemplate.exchange(url("/api/pcc/seminars"),
            HttpMethod.POST, jsonJwtEntity(VALID_BODY, member), String.class);
        assertThat(createResp.getStatusCode())
            .as("create — reçu %s, body=%s", createResp.getStatusCode(), createResp.getBody())
            .isEqualTo(HttpStatus.CREATED);
        JsonNode created = om.readTree(createResp.getBody());
        UUID id = UUID.fromString(created.get("id").asText());
        createdIds.add(id);
        assertThat(created.get("organizerId").asText()).isEqualTo(memberId.toString());
        assertThat(created.get("status").asText()).isEqualTo("demandee");
        assertThat(created.get("notesInternal").isNull()).as("notes masquées à la création").isTrue();

        // 2. GET /mine → contient la demande.
        ResponseEntity<String> mine = restTemplate.exchange(url("/api/pcc/seminars/mine"),
            HttpMethod.GET, jwtEntity(member), String.class);
        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mine.getBody()).contains(id.toString());

        // 3. CLIENT tente PATCH status → 403 (pas d'UPDATE:SEMINARS).
        assertThat(restTemplate.exchange(url("/api/pcc/seminars/" + id + "/status"),
            HttpMethod.PATCH, jsonJwtEntity("{\"status\":\"confirmee\"}", member), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 4. admin PATCH status → 200 (admin bypasse l'isolation tenant).
        ResponseEntity<String> statusResp = restTemplate.exchange(url("/api/pcc/seminars/" + id + "/status"),
            HttpMethod.PATCH,
            jsonJwtEntity("{\"status\":\"confirmee\",\"notesInternal\":\"Validé — salle réservée\"}", adminBearer()),
            String.class);
        assertThat(statusResp.getStatusCode())
            .as("status admin — reçu %s, body=%s", statusResp.getStatusCode(), statusResp.getBody())
            .isEqualTo(HttpStatus.OK);
        JsonNode updated = om.readTree(statusResp.getBody());
        assertThat(updated.get("status").asText()).isEqualTo("confirmee");
        assertThat(updated.get("notesInternal").asText()).isEqualTo("Validé — salle réservée");

        // 5. vue membre /mine : statut à jour MAIS notes internes masquées.
        ResponseEntity<String> mineAfter = restTemplate.exchange(url("/api/pcc/seminars/mine"),
            HttpMethod.GET, jwtEntity(member), String.class);
        JsonNode arr = om.readTree(mineAfter.getBody());
        JsonNode row = null;
        for (JsonNode n : arr) {
            if (id.toString().equals(n.get("id").asText())) { row = n; break; }
        }
        assertThat(row).as("demande retrouvée dans /mine").isNotNull();
        assertThat(row.get("status").asText()).isEqualTo("confirmee");
        assertThat(row.get("notesInternal").isNull()).as("notes internes masquées côté membre").isTrue();
    }

    // ─── D. inbox admin → 200 (tableau) ──────────────────────────────────────────

    @Test
    void inbox_admin_returns200() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(url("/api/pcc/seminars/inbox"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(resp.getBody()).isArray()).isTrue();
    }
}
