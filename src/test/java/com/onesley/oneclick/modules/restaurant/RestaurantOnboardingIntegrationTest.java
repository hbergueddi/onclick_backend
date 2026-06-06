package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration E1 — POST /api/restaurants/{id}/onboarding-complete (wizard 1re connexion owner).
 *
 * <p>Vérifie le contrat RBAC v2 ({@code UPDATE:RESTAURANTS}) + le garde ABAC
 * ({@code RestaurantAccessGuard.requireAdminOrActiveStaffOf}) :
 * <ul>
 *   <li>owner actif de SON resto → 200 + {@code onboarding_completed_at} posé en base ;</li>
 *   <li>restaurateur d'un AUTRE resto → 403 (ABAC) ;</li>
 *   <li>CLIENT (sans l'authority) → 403 (RBAC) ;</li>
 *   <li>sans token → 401.</li>
 * </ul>
 * Aucune mutation persistante : le flag est remis à NULL en début et fin de test.
 */
class RestaurantOnboardingIntegrationTest extends AbstractIntegrationTest {

    private record Resto(UUID restaurateurId, UUID ownRestaurantId) {}

    /** Un RESTAURATEUR + l'un de SES restaurants (où il est staff actif). */
    private Resto restaurateurWithRestaurant() {
        String[] p = jdbc.queryForObject(
            "SELECT rs.user_id::text || ',' || rs.restaurant_id::text FROM restaurant_staffs rs "
            + "JOIN users u ON u.id = rs.user_id JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'RESTAURATEUR' AND rs.deleted_at IS NULL LIMIT 1", String.class).split(",");
        return new Resto(UUID.fromString(p[0]), UUID.fromString(p[1]));
    }

    /** Un restaurant DONT ce restaurateur n'est PAS staff. */
    private UUID otherRestaurant(UUID notThisOne, UUID notStaffOfUser) {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT r.id::text FROM restaurants r WHERE r.deleted_at IS NULL AND r.id <> ?::uuid "
            + "AND NOT EXISTS (SELECT 1 FROM restaurant_staffs rs WHERE rs.restaurant_id = r.id "
            + "AND rs.user_id = ?::uuid AND rs.deleted_at IS NULL) LIMIT 1",
            String.class, notThisOne, notStaffOfUser));
    }

    private UUID anyClientId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' LIMIT 1", String.class));
    }

    private void resetOnboarding(UUID restaurantId) {
        jdbc.update("UPDATE restaurants SET onboarding_completed_at = NULL WHERE id = ?::uuid", restaurantId);
    }

    private String restaurateurBearer(UUID id) { return jwtIssuer.issueAccessToken(id, "RESTAURATEUR").token(); }

    private int post(String path, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.POST, jwtEntity(jwt), String.class)
            .getStatusCode().value();
    }

    // ─── Owner actif → 200 + timestamp posé en base ───────────────────────────

    @Test
    void completeOnboarding_ownRestaurant_owner_returns200_andStampsTimestamp() {
        Resto r = restaurateurWithRestaurant();
        resetOnboarding(r.ownRestaurantId());
        try {
            String path = "/api/restaurants/" + r.ownRestaurantId() + "/onboarding-complete";
            assertThat(post(path, restaurateurBearer(r.restaurateurId()))).isEqualTo(200);

            Integer set = jdbc.queryForObject(
                "SELECT COUNT(*) FROM restaurants WHERE id = ?::uuid AND onboarding_completed_at IS NOT NULL",
                Integer.class, r.ownRestaurantId());
            assertThat(set).isEqualTo(1);
        } finally {
            resetOnboarding(r.ownRestaurantId()); // pas d'effet de bord persistant
        }
    }

    // ─── ABAC : autre resto → 403 (et aucune mutation) ────────────────────────

    @Test
    void completeOnboarding_otherRestaurant_restaurateur_returns403() {
        Resto r = restaurateurWithRestaurant();
        UUID otherResto = otherRestaurant(r.ownRestaurantId(), r.restaurateurId());
        assertThat(post("/api/restaurants/" + otherResto + "/onboarding-complete",
            restaurateurBearer(r.restaurateurId()))).isEqualTo(403);

        Integer set = jdbc.queryForObject(
            "SELECT COUNT(*) FROM restaurants WHERE id = ?::uuid AND onboarding_completed_at IS NOT NULL",
            Integer.class, otherResto);
        assertThat(set).isZero();
    }

    // ─── RBAC : CLIENT n'a pas UPDATE:RESTAURANTS → 403 ───────────────────────

    @Test
    void completeOnboarding_client_returns403() {
        Resto r = restaurateurWithRestaurant();
        String clientJwt = jwtIssuer.issueAccessToken(anyClientId(), "CLIENT").token();
        assertThat(post("/api/restaurants/" + r.ownRestaurantId() + "/onboarding-complete", clientJwt))
            .isEqualTo(403);
    }

    // ─── Anonyme → 401 ────────────────────────────────────────────────────────

    @Test
    void completeOnboarding_noToken_returns401() {
        Resto r = restaurateurWithRestaurant();
        int status = restTemplate.exchange(
            url("/api/restaurants/" + r.ownRestaurantId() + "/onboarding-complete"),
            HttpMethod.POST, null, String.class).getStatusCode().value();
        assertThat(status).isEqualTo(401);
    }
}
