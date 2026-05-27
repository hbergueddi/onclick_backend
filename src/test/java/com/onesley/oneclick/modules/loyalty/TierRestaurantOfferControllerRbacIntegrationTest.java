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
 * Intégration RBAC + ABAC du domaine TIER_OFFER (V48).
 *
 * <p>SUPERADMIN : CRUD complet. RESTAURATEUR : lit SON restaurant (by-restaurant
 * + ABAC) mais ni la liste globale (admin-only) ni CREATE (SUPERADMIN-only).
 * CLIENT : pas de VIEW:TIER_OFFER → 403.
 */
class TierRestaurantOfferControllerRbacIntegrationTest extends AbstractIntegrationTest {

    private UUID createdId;
    private UUID anyRestaurantId;
    private String restoBearer;
    private UUID restoRestaurantId;

    @BeforeEach
    void setup() {
        anyRestaurantId = UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class));
        String pair = jdbc.queryForObject(
            "SELECT rs.user_id::text || ',' || rs.restaurant_id::text FROM restaurant_staffs rs "
            + "JOIN users u ON u.id = rs.user_id JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'RESTAURATEUR' AND rs.deleted_at IS NULL LIMIT 1", String.class);
        String[] p = pair.split(",");
        restoBearer = jwtIssuer.issueAccessToken(UUID.fromString(p[0]), "RESTAURATEUR").token();
        restoRestaurantId = UUID.fromString(p[1]);
    }

    @AfterEach
    void cleanup() {
        if (createdId != null) {
            jdbc.update("DELETE FROM tier_restaurant_offers WHERE id = ?", createdId);
            createdId = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void superadmin_fullCrud() {
        String admin = adminBearer();
        Map<String, Object> body = Map.of(
            "restaurantId", anyRestaurantId.toString(),
            "tierName", "T-" + UUID.randomUUID().toString().substring(0, 8),
            "offerLabel", "-10% sur l'addition",
            "offerType", "remise",
            "offerValue", "10",
            "enabled", true);
        ResponseEntity<Map> created = restTemplate.exchange(
            url("/api/tier-offers"), HttpMethod.POST, jsonJwtEntity(body, admin), Map.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        createdId = UUID.fromString((String) created.getBody().get("id"));

        assertThat(restTemplate.exchange(url("/api/tier-offers"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode().value()).isEqualTo(200);
        assertThat(restTemplate.exchange(url("/api/tier-offers/by-restaurant/" + anyRestaurantId),
            HttpMethod.GET, jwtEntity(admin), String.class).getStatusCode().value()).isEqualTo(200);

        ResponseEntity<Map> patched = restTemplate.exchange(url("/api/tier-offers/" + createdId),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("enabled", false), admin), Map.class);
        assertThat(patched.getStatusCode().value()).isEqualTo(200);
        assertThat(patched.getBody().get("enabled")).isEqualTo(false);

        assertThat(restTemplate.exchange(url("/api/tier-offers/" + createdId), HttpMethod.DELETE,
            jwtEntity(admin), String.class).getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void restaurateur_ownRestaurant_view_butListAndCreate_403() {
        // Lit SON restaurant → 200
        assertThat(restTemplate.exchange(url("/api/tier-offers/by-restaurant/" + restoRestaurantId),
            HttpMethod.GET, jwtEntity(restoBearer), String.class).getStatusCode().value()).isEqualTo(200);
        // Liste globale → 403 (admin-only)
        assertThat(restTemplate.exchange(url("/api/tier-offers"), HttpMethod.GET, jwtEntity(restoBearer), String.class)
            .getStatusCode().value()).isEqualTo(403);
        // CREATE → 403 (SUPERADMIN-only)
        assertThat(restTemplate.exchange(url("/api/tier-offers"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restoRestaurantId.toString(), "tierName", "Ruby", "offerLabel", "X"), restoBearer),
            String.class).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void restaurateur_otherRestaurant_returns403_abac() {
        assertThat(restTemplate.exchange(url("/api/tier-offers/by-restaurant/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(restoBearer), String.class).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void client_byRestaurant_returns403_noTierOfferAuthority() {
        assertThat(restTemplate.exchange(url("/api/tier-offers/by-restaurant/" + restoRestaurantId),
            HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class).getStatusCode().value()).isEqualTo(403);
    }
}
