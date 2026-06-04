package com.onesley.oneclick.modules.feedback;

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
 * RBAC/ABAC L4 — « Avis » (PCC Lot 7, ressource {@code FEEDBACK} seedée par V69).
 *
 * <p>Invariants vérifiés sur la stack réelle (filter chain → JwtDecoder → converter →
 * {@code @PreAuthorize} → service ABAC → DB) :
 * <ul>
 *   <li>Sans JWT → 401.</li>
 *   <li>Un rôle SANS {@code CREATE:FEEDBACK} (RESTAURATEUR) → 403 sur POST.</li>
 *   <li>CLIENT : flow create → /mine → reply(admin) → mark-read.</li>
 *   <li>CLIENT tente reply → 403 (a {@code UPDATE:FEEDBACK} mais pas owner/admin → ABAC service).</li>
 *   <li>mark-read par le membre auteur → 200 ; par un tiers → 403.</li>
 *   <li>GET /inbox owner/admin → 200 (lecture owner-scopée).</li>
 * </ul>
 *
 * <p>Données : membres PCC seedés ({@code memberN@palmeraie.com}, CLIENT, tenant palmeraie).
 * Les feedbacks créés par le test sont nettoyés en teardown (pas de soft-delete).</p>
 */
class PccFeedbackRbacIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private final java.util.List<UUID> createdFeedbackIds = new java.util.ArrayList<>();

    private UUID userIdByEmail(String email) {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM users WHERE email = ?", String.class, email));
    }

    /** Bearer pour un membre PCC précis (rôle CLIENT). */
    private String bearerFor(String email) {
        return jwtIssuer.issueAccessToken(userIdByEmail(email), "CLIENT").token();
    }

    @AfterEach
    void cleanup() {
        for (UUID id : createdFeedbackIds) {
            jdbc.update("DELETE FROM pcc_feedbacks WHERE id = ?", id);
        }
        createdFeedbackIds.clear();
    }

    // ─── A. 401 sans JWT ─────────────────────────────────────────────────────────

    @Test
    void noJwt_returns401() {
        assertThat(restTemplate.exchange(url("/api/pcc/feedbacks/mine"),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange(url("/api/pcc/feedbacks"),
            HttpMethod.POST, jsonJwtEntity("{\"sentiment\":\"happy\",\"category\":\"Padel\"}", null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── B. RBAC : rôle sans CREATE:FEEDBACK → 403 ───────────────────────────────

    @Test
    void create_byRoleWithoutCreate_forbidden() {
        // RESTAURATEUR détient VIEW/UPDATE:FEEDBACK mais PAS CREATE:FEEDBACK → 403.
        ResponseEntity<String> resp = restTemplate.exchange(url("/api/pcc/feedbacks"),
            HttpMethod.POST,
            jsonJwtEntity("{\"sentiment\":\"happy\",\"category\":\"Padel\"}", bearerForRole("RESTAURATEUR")),
            String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── C. CLIENT — flow create → /mine → reply(admin) → mark-read ──────────────

    @Test
    void client_create_list_reply_read_flow() throws Exception {
        String member = bearerFor("member1@palmeraie.com");
        UUID memberId = userIdByEmail("member1@palmeraie.com");

        // 1. create (avis général, target null) → 201.
        ResponseEntity<String> createResp = restTemplate.exchange(url("/api/pcc/feedbacks"),
            HttpMethod.POST,
            jsonJwtEntity("{\"sentiment\":\"unhappy\",\"category\":\"Service en restaurant\",\"comment\":\"Service lent ce midi.\"}", member),
            String.class);
        assertThat(createResp.getStatusCode())
            .as("create — reçu %s, body=%s", createResp.getStatusCode(), createResp.getBody())
            .isEqualTo(HttpStatus.CREATED);
        JsonNode created = om.readTree(createResp.getBody());
        UUID feedbackId = UUID.fromString(created.get("id").asText());
        createdFeedbackIds.add(feedbackId);
        assertThat(created.get("memberId").asText()).isEqualTo(memberId.toString());
        assertThat(created.get("sentiment").asText()).isEqualTo("unhappy");

        // 2. GET /mine → contient le feedback.
        ResponseEntity<String> mine = restTemplate.exchange(url("/api/pcc/feedbacks/mine"),
            HttpMethod.GET, jwtEntity(member), String.class);
        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mine.getBody()).contains(feedbackId.toString());

        // 3. CLIENT tente reply → 403 (UPDATE:FEEDBACK OK au niveau RBAC, mais ABAC service : pas owner/admin).
        assertThat(restTemplate.exchange(url("/api/pcc/feedbacks/" + feedbackId + "/reply"),
            HttpMethod.PATCH, jsonJwtEntity("{\"replyText\":\"Réponse interdite\"}", member), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 4. admin (SUPERADMIN) reply → 200 (admin → owner-scope bypassé).
        ResponseEntity<String> replyResp = restTemplate.exchange(url("/api/pcc/feedbacks/" + feedbackId + "/reply"),
            HttpMethod.PATCH, jsonJwtEntity("{\"replyText\":\"Merci, nous corrigeons cela.\"}", adminBearer()),
            String.class);
        assertThat(replyResp.getStatusCode())
            .as("reply admin — reçu %s, body=%s", replyResp.getStatusCode(), replyResp.getBody())
            .isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(replyResp.getBody()).get("replyText").asText()).isEqualTo("Merci, nous corrigeons cela.");

        // 5. un AUTRE membre tente mark-read → 403 (pas l'auteur).
        assertThat(restTemplate.exchange(url("/api/pcc/feedbacks/" + feedbackId + "/read"),
            HttpMethod.PATCH, jwtEntity(bearerFor("member2@palmeraie.com")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // 6. le membre auteur mark-read → 200, replyReadByMember = true.
        ResponseEntity<String> readResp = restTemplate.exchange(url("/api/pcc/feedbacks/" + feedbackId + "/read"),
            HttpMethod.PATCH, jwtEntity(member), String.class);
        assertThat(readResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(readResp.getBody()).get("replyReadByMember").asBoolean()).isTrue();
    }

    // ─── D. inbox owner/admin → 200 (lecture owner-scopée) ───────────────────────

    @Test
    void inbox_admin_returns200() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(url("/api/pcc/feedbacks/inbox"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(resp.getBody()).isArray()).isTrue();
    }
}
