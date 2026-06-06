package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration E1 — GET/PUT /api/restaurants/{id}/business-hours (étape Horaires du wizard).
 *
 * <p>Contrat RBAC v2 + ABAC :
 * <ul>
 *   <li>PUT owner actif de SON resto → 200 + lignes business_hours persistées ;</li>
 *   <li>GET owner → 200 ;</li>
 *   <li>PUT autre resto (restaurateur) → 403 (ABAC) ;</li>
 *   <li>PUT CLIENT → 403 (RBAC, pas UPDATE:RESTAURANTS) ;</li>
 *   <li>PUT sans token → 401 ;</li>
 *   <li>PUT end &le; start → 400 (garde-fou métier, pas un 500 du CHECK DB) ;</li>
 *   <li>PUT dayOfWeek hors 0-6 → 400 (Bean Validation).</li>
 * </ul>
 * Non destructif : aucune ligne business_hours n'est seedée pour les restaurants,
 * et les lignes créées sont purgées en {@code finally}.
 */
class RestaurantBusinessHoursIntegrationTest extends AbstractIntegrationTest {

    private record Resto(UUID restaurateurId, UUID ownRestaurantId) {}

    private Resto restaurateurWithRestaurant() {
        String[] p = jdbc.queryForObject(
            "SELECT rs.user_id::text || ',' || rs.restaurant_id::text FROM restaurant_staffs rs "
            + "JOIN users u ON u.id = rs.user_id JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'RESTAURATEUR' AND rs.deleted_at IS NULL LIMIT 1", String.class).split(",");
        return new Resto(UUID.fromString(p[0]), UUID.fromString(p[1]));
    }

    private UUID otherRestaurant(UUID notThisOne, UUID notStaffOfUser) {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT r.id::text FROM restaurants r WHERE r.deleted_at IS NULL AND r.id <> ?::uuid "
            + "AND NOT EXISTS (SELECT 1 FROM restaurant_staffs rs WHERE rs.restaurant_id = r.id "
            + "AND rs.user_id = ?::uuid AND rs.deleted_at IS NULL) LIMIT 1",
            String.class, notThisOne, notStaffOfUser));
    }

    private UUID anyClientId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id WHERE r.code = 'CLIENT' LIMIT 1",
            String.class));
    }

    private void purge(UUID restaurantId) {
        jdbc.update("DELETE FROM business_hours WHERE entity_type='restaurant' AND entity_id=?::uuid", restaurantId);
    }

    private int countHours(UUID restaurantId) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM business_hours WHERE entity_type='restaurant' AND entity_id=?::uuid",
            Integer.class, restaurantId);
    }

    private String restaurateurBearer(UUID id) { return jwtIssuer.issueAccessToken(id, "RESTAURATEUR").token(); }

    private int put(String path, Object body, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.PUT, jsonJwtEntity(body, jwt), String.class)
            .getStatusCode().value();
    }

    private Map<String, Object> validPayload() {
        return Map.of("hours", List.of(
            Map.of("dayOfWeek", 1, "startTime", "12:00:00", "endTime", "23:00:00"),
            Map.of("dayOfWeek", 2, "startTime", "12:00:00", "endTime", "15:00:00")));
    }

    // ─── PUT owner → 200 + persistance ────────────────────────────────────────

    @Test
    void putBusinessHours_owner_returns200_andPersists() {
        Resto r = restaurateurWithRestaurant();
        purge(r.ownRestaurantId());
        try {
            assertThat(put("/api/restaurants/" + r.ownRestaurantId() + "/business-hours",
                validPayload(), restaurateurBearer(r.restaurateurId()))).isEqualTo(200);
            assertThat(countHours(r.ownRestaurantId())).isEqualTo(2);
        } finally {
            purge(r.ownRestaurantId());
        }
    }

    // ─── GET owner → 200 ──────────────────────────────────────────────────────

    @Test
    void getBusinessHours_owner_returns200() {
        Resto r = restaurateurWithRestaurant();
        int status = restTemplate.exchange(
            url("/api/restaurants/" + r.ownRestaurantId() + "/business-hours"),
            HttpMethod.GET, jwtEntity(restaurateurBearer(r.restaurateurId())), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(200);
    }

    // ─── ABAC : autre resto → 403 (aucune mutation) ───────────────────────────

    @Test
    void putBusinessHours_otherRestaurant_returns403() {
        Resto r = restaurateurWithRestaurant();
        UUID otherResto = otherRestaurant(r.ownRestaurantId(), r.restaurateurId());
        assertThat(put("/api/restaurants/" + otherResto + "/business-hours",
            validPayload(), restaurateurBearer(r.restaurateurId()))).isEqualTo(403);
        assertThat(countHours(otherResto)).isZero();
    }

    // ─── RBAC : CLIENT → 403 ──────────────────────────────────────────────────

    @Test
    void putBusinessHours_client_returns403() {
        Resto r = restaurateurWithRestaurant();
        String clientJwt = jwtIssuer.issueAccessToken(anyClientId(), "CLIENT").token();
        assertThat(put("/api/restaurants/" + r.ownRestaurantId() + "/business-hours", validPayload(), clientJwt))
            .isEqualTo(403);
    }

    // ─── Anonyme → 401 ────────────────────────────────────────────────────────

    @Test
    void putBusinessHours_noToken_returns401() {
        Resto r = restaurateurWithRestaurant();
        int status = restTemplate.exchange(
            url("/api/restaurants/" + r.ownRestaurantId() + "/business-hours"),
            HttpMethod.PUT, null, String.class).getStatusCode().value();
        assertThat(status).isEqualTo(401);
    }

    // ─── Garde-fou métier : end ≤ start → 400 ─────────────────────────────────

    @Test
    void putBusinessHours_endNotAfterStart_returns400() {
        Resto r = restaurateurWithRestaurant();
        Map<String, Object> bad = Map.of("hours", List.of(
            Map.of("dayOfWeek", 1, "startTime", "20:00:00", "endTime", "20:00:00")));
        assertThat(put("/api/restaurants/" + r.ownRestaurantId() + "/business-hours",
            bad, restaurateurBearer(r.restaurateurId()))).isEqualTo(400);
    }

    // ─── Bean Validation : dayOfWeek hors 0-6 → 400 ───────────────────────────

    @Test
    void putBusinessHours_invalidDayOfWeek_returns400() {
        Resto r = restaurateurWithRestaurant();
        Map<String, Object> bad = Map.of("hours", List.of(
            Map.of("dayOfWeek", 9, "startTime", "12:00:00", "endTime", "23:00:00")));
        assertThat(put("/api/restaurants/" + r.ownRestaurantId() + "/business-hours",
            bad, restaurateurBearer(r.restaurateurId()))).isEqualTo(400);
    }
}
