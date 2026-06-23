package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC + ABAC self-scope pour {@code GET /api/loyalty/params}
 * (écran Pocket « Vos avantages » / {@code ConversionGuide}).
 *
 * <p>Contrat :
 * <ul>
 *   <li><b>RBAC</b> — garde {@code hasAuthority('VIEW:LOYALTY')} (autorité détenue par CLIENT
 *       pour ses lectures fidélité Pocket). CLIENT → 200 ; un rôle sans {@code VIEW:LOYALTY}
 *       (déterminé dynamiquement sur le graphe RBAC réel) → 403 ; anonyme → 401.</li>
 *   <li><b>ABAC self-scope</b> — l'endpoint n'expose AUCUN paramètre {@code clientId} : les
 *       params sont toujours calculés pour le {@code sub} du JWT ({@code currentUserId()}).
 *       Deux clients distincts obtiennent donc chacun leurs propres params (aucune
 *       énumération / lecture des params d'autrui possible).</li>
 * </ul>
 */
class LoyaltyParamsRbacIntegrationTest extends AbstractIntegrationTest {

    private UUID throwawayUserId;

    @AfterEach
    void cleanup() {
        if (throwawayUserId != null) jdbc.update("DELETE FROM users WHERE id = ?", throwawayUserId);
    }

    // ─── helpers ───────────────────────────────────────────────────────────

    private int get(String path, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.GET, jwtEntity(jwt), String.class)
            .getStatusCode().value();
    }

    private org.springframework.http.ResponseEntity<String> getFull(String path, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.GET, jwtEntity(jwt), String.class);
    }

    /** (clientId, restaurantId) d'un compte loyalty d'un vrai CLIENT. */
    private String[] clientAccountPair() {
        return jdbc.queryForObject(
            "SELECT la.client_id::text || ',' || la.restaurant_id::text "
            + "FROM loyalty_accounts la JOIN users u ON u.id = la.client_id "
            + "JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL LIMIT 1", String.class).split(",");
    }

    /** Code d'un rôle qui ne détient PAS VIEW:LOYALTY (vérifié sur le graphe permissions réel). */
    private String roleWithoutViewLoyalty() {
        return jdbc.queryForObject("""
            SELECT r.code FROM roles r
            WHERE NOT EXISTS (
              SELECT 1 FROM permissions p
              JOIN menus m  ON m.id = p.menu_id
              JOIN actions a ON a.id = p.action_id
              WHERE p.role_id = r.id AND m.code = 'LOYALTY' AND a.code = 'VIEW'
            )
            LIMIT 1
            """, String.class);
    }

    // ─── RBAC ────────────────────────────────────────────────────────────────

    @Test
    void params_client_returns200_withDtoShape() {
        String[] cr = clientAccountPair();
        var res = getFull("/api/loyalty/params?restaurantId=" + cr[1], clientBearerFor(UUID.fromString(cr[0])));
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody())
            .contains("conversionRatePct")
            .contains("pointValueMad")
            .contains("benefitDurationDays")
            .contains("tierName")
            .contains("tierBonusPct");
    }

    @Test
    void params_anonymous_returns401() {
        String[] cr = clientAccountPair();
        assertThat(restTemplate.exchange(url("/api/loyalty/params?restaurantId=" + cr[1]),
            HttpMethod.GET, org.springframework.http.HttpEntity.EMPTY, String.class)
            .getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void params_roleWithoutViewLoyalty_returns403() {
        String[] cr = clientAccountPair();
        String role = roleWithoutViewLoyalty();
        // Fixture jetable sur ce rôle (les autorités viennent du rôle DB du sub, pas du claim).
        throwawayUserId = UUID.fromString(jdbc.queryForObject(
            "INSERT INTO users (role_id, email, password_hash, first_name, last_name) "
            + "VALUES ((SELECT id FROM roles WHERE code = ?), ?, 'x', 'No', 'Loyalty') "
            + "RETURNING id::text",
            String.class, role, "rbac-noloyalty-" + UUID.randomUUID() + "@test.local"));
        String bearer = jwtIssuer.issueAccessToken(throwawayUserId, role).token();
        assertThat(get("/api/loyalty/params?restaurantId=" + cr[1], bearer)).isEqualTo(403);
    }

    // ─── ABAC self-scope : chaque client lit SES propres params ────────────────

    @Test
    void params_selfScope_twoClients_eachGetsOwn_noClientIdParam() {
        // L'endpoint n'a pas de paramètre clientId → impossible de lire les params d'autrui.
        // On vérifie que deux clients distincts obtiennent chacun un 200 sur le MÊME restaurant
        // (params calculés pour le sub du JWT, pas pour un id passé en query).
        String[] cr = clientAccountPair();
        UUID clientA = UUID.fromString(cr[0]);
        UUID clientB = UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL AND u.id <> ?::uuid LIMIT 1",
            String.class, clientA));

        assertThat(get("/api/loyalty/params?restaurantId=" + cr[1], clientBearerFor(clientA))).isEqualTo(200);
        assertThat(get("/api/loyalty/params?restaurantId=" + cr[1], clientBearerFor(clientB))).isEqualTo(200);
    }

    private String clientBearerFor(UUID id) {
        return jwtIssuer.issueAccessToken(id, "CLIENT").token();
    }
}
