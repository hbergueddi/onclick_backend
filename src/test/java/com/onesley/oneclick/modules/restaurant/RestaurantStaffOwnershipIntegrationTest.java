package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration P2 (owner-check sweep) — anti-prise-de-contrôle sur le STAFF resto.
 *
 * <p>RESTAURATEUR détient {@code CREATE/UPDATE/DELETE:STAFF} : sans contrôle de
 * tenance, il pouvait {@code POST /restaurants/{anyId}/staff} et s'ajouter
 * <b>owner de n'importe quel restaurant</b> (escalade / takeover), ou lister/gérer
 * le staff d'un resto tiers. On exige désormais staff actif / admin DU restaurant
 * ciblé ({@code RestaurantAccessGuard}).
 */
class RestaurantStaffOwnershipIntegrationTest extends AbstractIntegrationTest {

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

    private String restaurateurBearer(UUID id) { return jwtIssuer.issueAccessToken(id, "RESTAURATEUR").token(); }

    private int post(String path, Map<String, Object> body, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.POST, jsonJwtEntity(body, jwt), String.class)
            .getStatusCode().value();
    }

    private int get(String path, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.GET, jwtEntity(jwt), String.class)
            .getStatusCode().value();
    }

    // ─── Takeover : un restaurateur ne peut PAS s'ajouter staff d'un autre resto ──

    @Test
    void addStaff_otherRestaurant_restaurateur_returns403() {
        Resto r = restaurateurWithRestaurant();
        UUID otherResto = otherRestaurant(r.ownRestaurantId(), r.restaurateurId());
        // payload valide → on atteint le garde-fou (pas un 400 de validation) ; il s'auto-cible owner
        int status = post("/api/restaurants/" + otherResto + "/staff",
            Map.of("userId", r.restaurateurId().toString(), "roleCode", "owner"),
            restaurateurBearer(r.restaurateurId()));
        assertThat(status).isEqualTo(403);
    }

    @Test
    void listStaff_otherRestaurant_restaurateur_returns403() {
        Resto r = restaurateurWithRestaurant();
        UUID otherResto = otherRestaurant(r.ownRestaurantId(), r.restaurateurId());
        assertThat(get("/api/restaurants/" + otherResto + "/staff", restaurateurBearer(r.restaurateurId())))
            .isEqualTo(403);
    }

    // ─── Le restaurateur garde l'accès à SON resto ; l'admin à tout (anti-régression) ─

    @Test
    void listStaff_ownRestaurant_restaurateur_returns200() {
        Resto r = restaurateurWithRestaurant();
        assertThat(get("/api/restaurants/" + r.ownRestaurantId() + "/staff", restaurateurBearer(r.restaurateurId())))
            .isEqualTo(200);
    }

    @Test
    void listStaff_otherRestaurant_admin_returns200() {
        Resto r = restaurateurWithRestaurant();
        UUID otherResto = otherRestaurant(r.ownRestaurantId(), r.restaurateurId());
        assertThat(get("/api/restaurants/" + otherResto + "/staff", adminBearer())).isEqualTo(200);
    }
}
