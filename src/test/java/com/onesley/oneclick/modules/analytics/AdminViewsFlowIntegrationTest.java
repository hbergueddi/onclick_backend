package com.onesley.oneclick.modules.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/analytics} vues admin (stats-full, users, wallet, pool, hi-cockpit). */
class AdminViewsFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void adminViews_allAggregates_200() {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/analytics/admin-stats-full?period=mois"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/admin-stats-full"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/admin-users?limit=10"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/admin-wallet/summary"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/admin-wallet/transactions?limit=10"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/recycling-pool"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/admin-hi-cockpit"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminStatsFull_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/analytics/admin-stats-full"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminUsers_asClient_403() {
        assertThat(restTemplate.exchange(url("/api/analytics/admin-users?limit=5"), HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * B3 — la recherche admin par nom (LIKE '%q%' sur first/last/email) sert d'E2E
     * du chemin servi par les index trigramme V60. On cherche un fragment d'un user
     * réel et on vérifie qu'il est renvoyé.
     */
    @Test
    void adminUsers_searchByNameFragment_returnsMatch() throws Exception {
        // Un user réel + un fragment ≥ 3 car. de son prénom (servi par idx_users_first_name_trgm).
        java.util.Map<String, Object> u = jdbc.queryForMap(
            "SELECT id::text AS id, lower(substring(first_name from 1 for 4)) AS frag "
            + "FROM users WHERE length(first_name) >= 4 AND deleted_at IS NULL LIMIT 1");
        String uid = (String) u.get("id");
        String frag = (String) u.get("frag");
        ResponseEntity<String> res = restTemplate.exchange(
            url("/api/analytics/admin-users?search=" + frag + "&limit=2000"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        boolean found = false;
        for (var n : om.readTree(res.getBody())) if (uid.equals(n.get("id").asText())) found = true;
        assertThat(found).as("le user dont le prénom contient le fragment doit être trouvé").isTrue();
    }

    // ─── B1 — group-dashboard (rollup agrégé, anti N+1) ──────────────────────

    @Test
    void groupDashboard_admin_returns200_oneEntryPerRequestedId() throws Exception {
        String rid = jdbc.queryForObject(
            "SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class);
        ResponseEntity<String> res = restTemplate.exchange(
            url("/api/analytics/group-dashboard?restaurantIds=" + rid),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var arr = om.readTree(res.getBody());
        assertThat(arr.isArray()).isTrue();
        assertThat(arr).hasSize(1);
        assertThat(arr.get(0).get("restaurantId").asText()).isEqualTo(rid);
        // Champs présents (rollup, valeurs ≥ 0).
        assertThat(arr.get(0).has("totalCA")).isTrue();
        assertThat(arr.get(0).get("reservations").asLong()).isGreaterThanOrEqualTo(0);
    }

    /**
     * Cœur B1 : un GROUP_ADMIN détient VIEW:RESTAURANTS (≠ VIEW:ANALYTICS réservé SUPERADMIN)
     * → atteint l'endpoint (200). GROUP_ADMIN est admin-tier ({@code SecurityHelper.isAdmin()}
     * couvre SUPERADMIN+GROUP_ADMIN), donc il bypasse l'ABAC — convention plateforme partagée
     * par tous les endpoints admin (AdminViews/LoyaltyExtension). On documente ce comportement.
     */
    @Test
    void groupDashboard_groupAdmin_holdsViewRestaurants_200() {
        String gaBearer = bearerForRole("GROUP_ADMIN");
        String anyRid = jdbc.queryForObject(
            "SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class);
        assertThat(restTemplate.exchange(url("/api/analytics/group-dashboard?restaurantIds=" + anyRid),
            HttpMethod.GET, jwtEntity(gaBearer), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * Scoping ABAC réel : un RESTAURATEUR (non-admin) voit le rollup de SON restaurant (200)
     * mais pas d'un resto hors périmètre (403). C'est le test de fuite cross-tenant.
     */
    @Test
    void groupDashboard_restaurateur_ownRestaurant200_foreignRestaurant403() {
        java.util.Map<String, Object> row = jdbc.queryForMap(
            "SELECT u.id::text AS uid, rs.restaurant_id::text AS rid "
            + "FROM users u JOIN roles r ON r.id = u.role_id "
            + "JOIN restaurant_staffs rs ON rs.user_id = u.id "
            + "WHERE r.code = 'RESTAURATEUR' AND rs.deleted_at IS NULL AND u.deleted_at IS NULL "
            + "ORDER BY u.id LIMIT 1");
        String ownerId = (String) row.get("uid");
        String ownedRid = (String) row.get("rid");
        String ownerBearer = jwtIssuer.issueAccessToken(UUID.fromString(ownerId), "RESTAURATEUR").token();

        String foreignRid = jdbc.queryForObject(
            "SELECT id::text FROM restaurants WHERE deleted_at IS NULL "
            + "AND id NOT IN (SELECT restaurant_id FROM restaurant_staffs WHERE user_id = ?::uuid AND deleted_at IS NULL) "
            + "LIMIT 1", String.class, ownerId);

        // 200 sur son resto → VIEW:RESTAURANTS + ABAC staff-actif OK.
        assertThat(restTemplate.exchange(url("/api/analytics/group-dashboard?restaurantIds=" + ownedRid),
            HttpMethod.GET, jwtEntity(ownerBearer), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        // 403 sur un resto étranger → ABAC bloque (pas staff).
        assertThat(restTemplate.exchange(url("/api/analytics/group-dashboard?restaurantIds=" + foreignRid),
            HttpMethod.GET, jwtEntity(ownerBearer), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void groupDashboard_noBearer_401() {
        String rid = jdbc.queryForObject(
            "SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class);
        assertThat(restTemplate.exchange(url("/api/analytics/group-dashboard?restaurantIds=" + rid),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
