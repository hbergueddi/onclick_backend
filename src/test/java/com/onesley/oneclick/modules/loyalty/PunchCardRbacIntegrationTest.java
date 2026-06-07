package com.onesley.oneclick.modules.loyalty;

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
 * RBAC/ABAC L4 — cartes de fidélité « punch cards » 10/1 (PCC Lot 3, ressource {@code PUNCH_CARDS}
 * seedée par V67).
 *
 * <p>Invariants vérifiés :
 * <ul>
 *   <li>CLIENT (VIEW:PUNCH_CARDS) voit SES cartes → 200, self-scope.</li>
 *   <li>CLIENT NE peut PAS redeem (pas UPDATE:PUNCH_CARDS) → 403.</li>
 *   <li>STAFF/admin (UPDATE:PUNCH_CARDS) redeem un palier complet → 200 ; sous-palier → 422.</li>
 *   <li>Sans JWT → 401.</li>
 * </ul>
 * </p>
 */
class PunchCardRbacIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    /** Cartes créées par le test → supprimées en teardown (la table n'a pas de soft delete). */
    private final java.util.List<UUID> createdCardIds = new java.util.ArrayList<>();

    /** Un CLIENT réel avec un tenant non-null, déterministe — sub du JWT {@link #clientBearer()}. */
    private UUID clientUserId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL AND u.tenant_id IS NOT NULL "
            + "ORDER BY u.id LIMIT 1", String.class));
    }

    private UUID clientTenantId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT u.tenant_id::text FROM users u WHERE u.id = ?", String.class, clientUserId()));
    }

    private String clientBearer() {
        return jwtIssuer.issueAccessToken(clientUserId(), "CLIENT").token();
    }

    /** Insère une carte (tenant client, client, activité) avec un count donné ; renvoie son id. */
    private UUID seedCard(String activity, int count, int redeemed) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO loyalty_punch_cards "
            + "(id, tenant_id, client_id, activity, count_punched, threshold, redeemed_count, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, 10, ?, now(), now())",
            id, clientTenantId(), clientUserId(), activity, count, redeemed);
        createdCardIds.add(id);
        return id;
    }

    @AfterEach
    void cleanup() {
        for (UUID id : createdCardIds) {
            jdbc.update("DELETE FROM loyalty_punch_cards WHERE id = ?", id);
        }
        createdCardIds.clear();
    }

    // ─── A. CLIENT voit SES cartes ──────────────────────────────────────────────

    @Test
    void client_canListOwnCards() throws Exception {
        seedCard("padel", 3, 0);
        seedCard("spa", 10, 0);

        ResponseEntity<String> resp = restTemplate.exchange(url("/api/punch-cards"),
            HttpMethod.GET, jwtEntity(clientBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode arr = om.readTree(resp.getBody());
        assertThat(arr.isArray()).isTrue();
        assertThat(arr).hasSizeGreaterThanOrEqualTo(2);
        // Les cartes exposent activity/count/threshold/remaining — pas de tenant/client (pas de PII de scope).
        JsonNode first = arr.get(0);
        assertThat(first.fieldNames()).toIterable().containsExactlyInAnyOrder(
            "id", "activity", "countPunched", "threshold", "redeemedCount", "remaining", "lastPunchedAt");
        assertThat(resp.getBody()).doesNotContain("tenantId", "clientId");
    }

    // ─── B. CLIENT ne peut PAS redeem ───────────────────────────────────────────

    @Test
    void client_cannotRedeem_forbidden() {
        UUID cardId = seedCard("padel", 10, 0); // palier complet, mais le CLIENT n'a pas le droit
        ResponseEntity<String> resp = restTemplate.exchange(url("/api/punch-cards/" + cardId + "/redeem"),
            HttpMethod.POST, jwtEntity(clientBearer()), String.class);
        // 403 : le CLIENT n'a pas UPDATE:PUNCH_CARDS (bloqué par @PreAuthorize avant le service).
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // La carte n'a pas bougé.
        Integer redeemed = jdbc.queryForObject(
            "SELECT redeemed_count FROM loyalty_punch_cards WHERE id = ?", Integer.class, cardId);
        assertThat(redeemed).isZero();
    }

    // ─── C. STAFF/admin redeem : palier OK (200) / sous-palier (422) ────────────

    @Test
    void staffAdmin_redeemAtPalier_succeeds() throws Exception {
        UUID cardId = seedCard("padel", 10, 0); // 1 palier complet

        ResponseEntity<String> resp = restTemplate.exchange(url("/api/punch-cards/" + cardId + "/redeem"),
            HttpMethod.POST, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(resp.getBody()).get("redeemedCount").asInt()).isEqualTo(1);

        // RESTAURATEUR (staff opérationnel) peut aussi redeem un autre palier complet.
        UUID card2 = seedCard("spa", 20, 1); // 20 - 10 = 10 ≥ 10 → 2e palier dispo
        ResponseEntity<String> resp2 = restTemplate.exchange(url("/api/punch-cards/" + card2 + "/redeem"),
            HttpMethod.POST, jwtEntity(bearerForRole("RESTAURATEUR")), String.class);
        assertThat(resp2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(resp2.getBody()).get("redeemedCount").asInt()).isEqualTo(2);
    }

    @Test
    void staffAdmin_redeemBelowPalier_unprocessable422() {
        UUID cardId = seedCard("padel", 7, 0); // < 10 → pas de palier complet
        ResponseEntity<String> resp = restTemplate.exchange(url("/api/punch-cards/" + cardId + "/redeem"),
            HttpMethod.POST, jwtEntity(adminBearer()), String.class);
        // 422 par code numérique (Spring Boot 4 : l'enum est UNPROCESSABLE_CONTENT, même code 422).
        assertThat(resp.getStatusCode().value())
            .as("redeem sous-palier — reçu %s, body=%s", resp.getStatusCode(), resp.getBody())
            .isEqualTo(422);
    }

    // ─── D. 401 sans JWT ─────────────────────────────────────────────────────────

    @Test
    void noJwt_returns401() {
        assertThat(restTemplate.exchange(url("/api/punch-cards"),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThat(restTemplate.exchange(url("/api/punch-cards/" + UUID.randomUUID() + "/redeem"),
            HttpMethod.POST, jwtEntity(null), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── E. by-tenant (export STAFF — Gap #3, VIEW:STAFF) ───────────────────────

    @Test
    void byTenant_staff_returnsCardsWithClientName() throws Exception {
        seedCard("padel", 4, 0);
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/punch-cards/by-tenant/" + clientTenantId()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode arr = om.readTree(resp.getBody());
        assertThat(arr.isArray()).isTrue();
        assertThat(arr).hasSizeGreaterThanOrEqualTo(1);
        // Vue admin : expose clientName (≠ self-view qui n'a pas l'identité).
        assertThat(arr.get(0).has("clientName")).isTrue();
        assertThat(arr.get(0).has("activity")).isTrue();
    }

    @Test
    void byTenant_client_forbidden() {
        // Le CLIENT n'a pas VIEW:STAFF → 403 (pas de fuite cross-membres).
        int status = restTemplate.exchange(
            url("/api/punch-cards/by-tenant/" + clientTenantId()),
            HttpMethod.GET, jwtEntity(clientBearer()), String.class).getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }
}
