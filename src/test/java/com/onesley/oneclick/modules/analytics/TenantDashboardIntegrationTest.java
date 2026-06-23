package com.onesley.oneclick.modules.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC/intégration L4 — cockpit tenant-admin {@code GET /api/analytics/tenant-dashboard} (C4.0,
 * ressource {@code TENANTS}). Stack réelle ({@code oneclick_enterprise}) :
 * <ul>
 *   <li>SUPERADMIN → 200 + forme JSON attendue (KPIs + listes + alertes) ;</li>
 *   <li>rôle sans {@code VIEW:TENANTS} (RESTAURATEUR) → 403 ;</li>
 *   <li>sans JWT → 401 ;</li>
 *   <li>tenant-scoping : les données d'un tenant ne fuient pas vers un autre (top restos du
 *       tenant ciblé ⊂ restaurants de ce tenant uniquement).</li>
 * </ul>
 */
class TenantDashboardIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private UUID tenantId(String slug) {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = ? AND deleted_at IS NULL", String.class, slug));
    }

    @Test
    void tenantDashboard_admin_returns200_withExpectedShape() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/analytics/tenant-dashboard?tenantId=" + tenantId("palmeraie")),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode())
            .as("reçu %s, body=%s", resp.getStatusCode(), resp.getBody())
            .isEqualTo(HttpStatus.OK);

        JsonNode body = om.readTree(resp.getBody());
        // KPIs présents (scalaires) — forme verbatim pour le wiring front.
        assertThat(body.has("ca30j")).isTrue();
        assertThat(body.has("reservations30j")).isTrue();
        assertThat(body.has("clientsActifs30j")).isTrue();
        assertThat(body.has("pointsDistribues30j")).isTrue();
        assertThat(body.has("restaurantsActifs")).isTrue();
        assertThat(body.has("restaurantsTotal")).isTrue();
        assertThat(body.has("offresActives")).isTrue();
        // deltas (peuvent être null) — clés présentes.
        assertThat(body.has("deltaCa")).isTrue();
        assertThat(body.has("deltaReservations")).isTrue();
        assertThat(body.has("deltaClients")).isTrue();
        assertThat(body.has("deltaPoints")).isTrue();
        // collections.
        assertThat(body.get("dailyTrend").isArray()).isTrue();
        assertThat(body.get("topRestaurants").isArray()).isTrue();
        assertThat(body.get("topClients").isArray()).isTrue();
        assertThat(body.get("upcomingReservations").isArray()).isTrue();
        assertThat(body.get("alerts").isArray()).isTrue();
        // dailyTrend = 30 points pleins (séries générées par generate_series).
        assertThat(body.get("dailyTrend")).hasSize(30);
        // top 5 max.
        assertThat(body.get("topRestaurants").size()).isLessThanOrEqualTo(5);
        assertThat(body.get("topClients").size()).isLessThanOrEqualTo(5);
        // alerts non vide (au minimum l'info « tout est sous contrôle »).
        assertThat(body.get("alerts").size()).isGreaterThanOrEqualTo(1);
        assertThat(body.get("alerts").get(0).has("type")).isTrue();
        assertThat(body.get("alerts").get(0).has("title")).isTrue();
    }

    @Test
    void tenantDashboard_topRestaurants_areScopedToTenant() throws Exception {
        UUID palmeraie = tenantId("palmeraie");
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/analytics/tenant-dashboard?tenantId=" + palmeraie),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode topRestaurants = om.readTree(resp.getBody()).get("topRestaurants");
        // Chaque resto retourné DOIT appartenir au tenant ciblé (pas de fuite cross-tenant).
        for (JsonNode r : topRestaurants) {
            UUID restoId = UUID.fromString(r.get("id").asText());
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM restaurants WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL",
                Integer.class, restoId, palmeraie);
            assertThat(cnt)
                .as("resto %s du dashboard doit appartenir au tenant palmeraie", restoId)
                .isEqualTo(1);
        }
    }

    @Test
    void tenantDashboard_upcomingReservations_belongToTenant() throws Exception {
        UUID palmeraie = tenantId("palmeraie");
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/analytics/tenant-dashboard?tenantId=" + palmeraie),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode upcoming = om.readTree(resp.getBody()).get("upcomingReservations");
        for (JsonNode r : upcoming) {
            UUID resaId = UUID.fromString(r.get("id").asText());
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reservations WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL",
                Integer.class, resaId, palmeraie);
            assertThat(cnt)
                .as("réservation %s du dashboard doit appartenir au tenant palmeraie", resaId)
                .isEqualTo(1);
            // statut ⊂ {pending, confirmed, counter_proposed}.
            assertThat(r.get("status").asText())
                .isIn("pending", "confirmed", "counter_proposed");
        }
    }

    @Test
    void tenantDashboard_nonSuperAdmin_forbidden() {
        assertThat(restTemplate.exchange(
            url("/api/analytics/tenant-dashboard?tenantId=" + tenantId("palmeraie")),
            HttpMethod.GET, jwtEntity(bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void tenantDashboard_noJwt_unauthorized() {
        assertThat(restTemplate.exchange(
            url("/api/analytics/tenant-dashboard?tenantId=" + tenantId("palmeraie")),
            HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
