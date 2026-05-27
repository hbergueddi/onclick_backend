package com.onesley.oneclick.modules.restaurant;

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
 * Intégration RBAC (autorité STAFF) + ABAC (RestaurantAccessGuard) des invitations
 * d'équipe (V47). Un RESTAURATEUR ne gère que SON restaurant ; CLIENT n'a aucune
 * autorité STAFF d'écriture.
 */
class TeamInvitationControllerRbacIntegrationTest extends AbstractIntegrationTest {

    private String restoBearer;
    private UUID restaurantId;
    private UUID createdId;

    @BeforeEach
    void setup() {
        // (user RESTAURATEUR, restaurant dont il est staff actif) — garantit RBAC + ABAC.
        String pair = jdbc.queryForObject(
            "SELECT rs.user_id::text || ',' || rs.restaurant_id::text FROM restaurant_staffs rs "
            + "JOIN users u ON u.id = rs.user_id JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'RESTAURATEUR' AND rs.deleted_at IS NULL LIMIT 1", String.class);
        String[] parts = pair.split(",");
        restoBearer = jwtIssuer.issueAccessToken(UUID.fromString(parts[0]), "RESTAURATEUR").token();
        restaurantId = UUID.fromString(parts[1]);
    }

    @AfterEach
    void cleanup() {
        if (createdId != null) {
            jdbc.update("DELETE FROM team_invitations WHERE id = ?", createdId);
            createdId = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void restaurateur_ownRestaurant_fullFlow() {
        // CREATE (ABAC : son restaurant) → 201
        ResponseEntity<Map> created = restTemplate.exchange(
            url("/api/team-invitations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restaurantId.toString(), "firstName", "Invite", "role", "serveur"), restoBearer),
            Map.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody()).isNotNull();
        createdId = UUID.fromString((String) created.getBody().get("id"));
        assertThat(created.getBody().get("status")).isEqualTo("pending");

        // LIST → 200
        int list = restTemplate.exchange(
            url("/api/team-invitations/by-restaurant/" + restaurantId), HttpMethod.GET,
            jwtEntity(restoBearer), String.class).getStatusCode().value();
        assertThat(list).isEqualTo(200);

        // PATCH status=disabled → 200
        ResponseEntity<Map> patched = restTemplate.exchange(
            url("/api/team-invitations/" + createdId), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "disabled"), restoBearer), Map.class);
        assertThat(patched.getStatusCode().value()).isEqualTo(200);
        assertThat(patched.getBody().get("status")).isEqualTo("disabled");
    }

    @Test
    void restaurateur_otherRestaurant_returns403_abac() {
        // ABAC : restaurant aléatoire dont il n'est pas staff → 403 (autorité OK, scope KO).
        int status = restTemplate.exchange(
            url("/api/team-invitations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", UUID.randomUUID().toString(), "firstName", "X"), restoBearer),
            String.class).getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void client_create_returns403_noStaffAuthority() {
        int status = restTemplate.exchange(
            url("/api/team-invitations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", restaurantId.toString(), "firstName", "X"), bearerForRole("CLIENT")),
            String.class).getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }
}
