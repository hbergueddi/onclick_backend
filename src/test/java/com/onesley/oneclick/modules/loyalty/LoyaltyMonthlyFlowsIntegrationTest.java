package com.onesley.oneclick.modules.loyalty;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration Gap #9 — flux mensuels de points (GET /api/loyalty/monthly-flows).
 *
 * <p>Vérifie le contrat : 12 mois renvoyés (generate_series), ordre croissant, agrégation
 * par type ({@code earn}/{@code spend}), et RBAC (VIEW:ANALYTICS — admin only ; CLIENT 403 ; no-JWT 401).
 */
class LoyaltyMonthlyFlowsIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private UUID restaurantId;
    private UUID accountId;

    @BeforeEach
    void setup() {
        UUID tenantId = UUID.fromString(jdbc.queryForObject("SELECT id::text FROM tenants LIMIT 1", String.class));
        UUID clientId = UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id=u.role_id "
            + "WHERE r.code='CLIENT' AND u.deleted_at IS NULL LIMIT 1", String.class));
        restaurantId = UUID.randomUUID();
        jdbc.update("INSERT INTO restaurants (id, tenant_id, name, city) VALUES (?, ?, ?, ?)",
            restaurantId, tenantId, "GAP9-Resto-" + restaurantId, "Casablanca");
        accountId = UUID.randomUUID();
        jdbc.update("INSERT INTO loyalty_accounts (id, client_id, restaurant_id, balance) VALUES (?, ?, ?, ?)",
            accountId, clientId, restaurantId, 70);
        // Mouvements ce mois : +100 earn, -30 spend → earned≥100, redeemed≥30 sur le dernier mois.
        jdbc.update("INSERT INTO loyalty_transactions (id, account_id, type, points, reason) VALUES (?, ?, 'earn', 100, 'gap9')",
            UUID.randomUUID(), accountId);
        jdbc.update("INSERT INTO loyalty_transactions (id, account_id, type, points, reason) VALUES (?, ?, 'spend', -30, 'gap9')",
            UUID.randomUUID(), accountId);
    }

    @AfterEach
    void cleanup() {
        if (accountId != null) jdbc.update("DELETE FROM loyalty_transactions WHERE account_id = ?", accountId);
        if (accountId != null) jdbc.update("DELETE FROM loyalty_accounts WHERE id = ?", accountId);
        if (restaurantId != null) jdbc.update("DELETE FROM restaurants WHERE id = ?", restaurantId);
        accountId = null;
        restaurantId = null;
    }

    @Test
    void noJwt_returns401() {
        assertThat(restTemplate.exchange(url("/api/loyalty/monthly-flows"),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void client_returns403() {
        assertThat(restTemplate.exchange(url("/api/loyalty/monthly-flows"),
            HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void admin_returns12Months_ascending_withAggregates() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(url("/api/loyalty/monthly-flows"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode arr = om.readTree(resp.getBody());
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isEqualTo(12);
        // Ordre croissant des mois.
        assertThat(arr.get(0).get("monthStart").asText())
            .isLessThan(arr.get(11).get("monthStart").asText());
        // Dernier mois (courant) : agrège les mouvements seedés (+100 earn, -30 spend → 30 absolu).
        JsonNode current = arr.get(11);
        assertThat(current.get("pointsEarned").asLong()).isGreaterThanOrEqualTo(100L);
        assertThat(current.get("pointsRedeemed").asLong()).isGreaterThanOrEqualTo(30L);
        assertThat(current.get("pointsExpired").asLong()).isGreaterThanOrEqualTo(0L);
    }
}
