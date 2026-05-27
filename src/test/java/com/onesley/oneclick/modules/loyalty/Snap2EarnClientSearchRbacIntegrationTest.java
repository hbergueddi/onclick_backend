package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC + ABAC de la recherche client Snap2Earn
 * ({@code GET /api/loyalty/clients/search}).
 *
 * <p>Contrat : un RESTAURATEUR (CREATE:LOYALTY) cherche les clients pour SON
 * restaurant (ABAC) et retrouve le client par nom. Restaurant tiers → 403.
 * CLIENT (pas de CREATE:LOYALTY) → 403. Fixture client jetable pour un test
 * déterministe (indépendant des données seed).
 */
class Snap2EarnClientSearchRbacIntegrationTest extends AbstractIntegrationTest {

    private static final String FIXTURE_NAME = "Zsnap2earnfixture";

    private String restoBearer;
    private UUID restaurantId;
    private UUID clientId;

    @BeforeEach
    void setup() {
        String pair = jdbc.queryForObject(
            "SELECT rs.user_id::text || ',' || rs.restaurant_id::text FROM restaurant_staffs rs "
            + "JOIN users u ON u.id = rs.user_id JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'RESTAURATEUR' AND rs.deleted_at IS NULL LIMIT 1", String.class);
        String[] p = pair.split(",");
        restoBearer = jwtIssuer.issueAccessToken(UUID.fromString(p[0]), "RESTAURATEUR").token();
        restaurantId = UUID.fromString(p[1]);

        // Client fixture jetable (rôle CLIENT, prénom distinctif → match déterministe).
        clientId = UUID.fromString(jdbc.queryForObject(
            "INSERT INTO users (role_id, email, password_hash, first_name, last_name) "
            + "VALUES ((SELECT id FROM roles WHERE code = 'CLIENT'), ?, 'x', ?, 'Client') RETURNING id::text",
            String.class, "snap2earn-" + UUID.randomUUID() + "@test.local", FIXTURE_NAME));
    }

    @AfterEach
    void cleanup() {
        if (clientId != null) {
            jdbc.update("DELETE FROM users WHERE id = ?", clientId);
            clientId = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void restaurateur_search_ownRestaurant_findsClient() {
        ResponseEntity<Map[]> res = restTemplate.exchange(
            url("/api/loyalty/clients/search?q=" + FIXTURE_NAME + "&restaurantId=" + restaurantId),
            HttpMethod.GET, jwtEntity(restoBearer), Map[].class);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(Arrays.stream(res.getBody())
            .anyMatch(m -> clientId.toString().equals(m.get("id")))).isTrue();
    }

    @Test
    void restaurateur_otherRestaurant_returns403_abac() {
        int status = restTemplate.exchange(
            url("/api/loyalty/clients/search?q=06&restaurantId=" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(restoBearer), String.class).getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void client_search_returns403_noLoyaltyAuthority() {
        int status = restTemplate.exchange(
            url("/api/loyalty/clients/search?q=06&restaurantId=" + restaurantId),
            HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class).getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }
}
