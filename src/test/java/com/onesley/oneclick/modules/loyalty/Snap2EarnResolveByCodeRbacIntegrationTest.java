package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC + ABAC de la résolution d'un client par Code OneClick
 * ({@code GET /api/loyalty/clients/by-code}) — remplace le RPC legacy
 * {@code find_client_by_code}.
 *
 * <p>Contrat : un RESTAURATEUR (CREATE:LOYALTY) scanne le QR / la Carte Wallet
 * d'un client pour SON restaurant (ABAC) et retrouve le client par son
 * {@code referral_code}. Restaurant tiers → 403. CLIENT (pas de CREATE:LOYALTY)
 * → 403. Code inconnu → 404. Fixture client jetable pour un test déterministe.
 */
class Snap2EarnResolveByCodeRbacIntegrationTest extends AbstractIntegrationTest {

    private String restoBearer;
    private UUID restaurantId;
    private UUID clientId;
    private String referralCode;

    @BeforeEach
    void setup() {
        String pair = jdbc.queryForObject(
            "SELECT rs.user_id::text || ',' || rs.restaurant_id::text FROM restaurant_staffs rs "
            + "JOIN users u ON u.id = rs.user_id JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'RESTAURATEUR' AND rs.deleted_at IS NULL LIMIT 1", String.class);
        String[] p = pair.split(",");
        restoBearer = jwtIssuer.issueAccessToken(UUID.fromString(p[0]), "RESTAURATEUR").token();
        restaurantId = UUID.fromString(p[1]);

        // Client fixture jetable (rôle CLIENT, code OneClick distinctif).
        referralCode = "BYC" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        clientId = UUID.fromString(jdbc.queryForObject(
            "INSERT INTO users (role_id, email, password_hash, first_name, last_name, referral_code) "
            + "VALUES ((SELECT id FROM roles WHERE code = 'CLIENT'), ?, 'x', 'Bycode', 'Client', ?) RETURNING id::text",
            String.class, "bycode-" + UUID.randomUUID() + "@test.local", referralCode));
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
    void restaurateur_byCode_ownRestaurant_findsClient() {
        ResponseEntity<Map> res = restTemplate.exchange(
            url("/api/loyalty/clients/by-code?code=" + referralCode + "&restaurantId=" + restaurantId),
            HttpMethod.GET, jwtEntity(restoBearer), Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody().get("id")).isEqualTo(clientId.toString());
        assertThat(res.getBody().get("firstName")).isEqualTo("Bycode");
    }

    @Test
    void restaurateur_otherRestaurant_returns403_abac() {
        int status = restTemplate.exchange(
            url("/api/loyalty/clients/by-code?code=" + referralCode + "&restaurantId=" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(restoBearer), String.class).getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void client_returns403_noLoyaltyAuthority() {
        int status = restTemplate.exchange(
            url("/api/loyalty/clients/by-code?code=" + referralCode + "&restaurantId=" + restaurantId),
            HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class).getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void unknownCode_ownRestaurant_returns404() {
        int status = restTemplate.exchange(
            url("/api/loyalty/clients/by-code?code=NOPE9999&restaurantId=" + restaurantId),
            HttpMethod.GET, jwtEntity(restoBearer), String.class).getStatusCode().value();
        assertThat(status).isEqualTo(404);
    }
}
