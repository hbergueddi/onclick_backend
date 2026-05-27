package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC A(a) — demande de notif push promo (PromoNotificationController).
 *
 * <p>Contrat : un RESTAURATEUR peut SOUMETTRE une demande pour SON restaurant
 * ({@code CREATE:OFFERS} + ABAC), mais ne peut PAS la modérer ({@code review} =
 * admin-only, sinon auto-approbation). CLIENT n'a pas {@code CREATE:OFFERS} → 403.
 */
class PromoNotificationRbacIntegrationTest extends AbstractIntegrationTest {

    private String restoBearer;
    private UUID restaurantId;
    private UUID createdId;

    @BeforeEach
    void setup() {
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
            jdbc.update("DELETE FROM promo_notification_requests WHERE id = ?", createdId);
            createdId = null;
        }
    }

    private Map<String, Object> body(String rid) {
        Map<String, Object> m = new HashMap<>();
        m.put("restaurantId", rid);
        m.put("title", "Promo test");
        m.put("body", "Demande de notification push");
        m.put("segment", "all");
        return m;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void restaurateur_createOwn_201_but_review_403() {
        ResponseEntity<Map> created = restTemplate.exchange(
            url("/api/notifications/promo-requests"), HttpMethod.POST,
            jsonJwtEntity(body(restaurantId.toString()), restoBearer), Map.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody()).isNotNull();
        createdId = UUID.fromString((String) created.getBody().get("id"));

        // Un restaurateur ne peut PAS approuver sa propre demande (modération admin-only).
        int review = restTemplate.exchange(
            url("/api/notifications/promo-requests/" + createdId + "/review"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "approved", "reviewedBy", UUID.randomUUID().toString()), restoBearer),
            String.class).getStatusCode().value();
        assertThat(review).isEqualTo(403);
    }

    @Test
    void restaurateur_otherRestaurant_returns403_abac() {
        int status = restTemplate.exchange(
            url("/api/notifications/promo-requests"), HttpMethod.POST,
            jsonJwtEntity(body(UUID.randomUUID().toString()), restoBearer), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void client_create_returns403_noOffersAuthority() {
        int status = restTemplate.exchange(
            url("/api/notifications/promo-requests"), HttpMethod.POST,
            jsonJwtEntity(body(restaurantId.toString()), bearerForRole("CLIENT")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }
}
