package com.onesley.oneclick.modules.resource_booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1.4 (Lot C) — endpoint d'agrégation no-show {@code GET /api/resource-bookings/no-show-stats}.
 *
 * <p>Invariants : gardé {@code VIEW:BOOKINGS} (401 sans JWT) + ABAC staff/admin côté service (un
 * CLIENT → 403) ; le <b>scope tenant vient du JWT</b> (jamais d'un paramètre client) → un
 * RESTAURATEUR ne voit QUE l'assiduité de SON tenant (oneclick) ; un no-show créé dans un AUTRE
 * tenant (palmeraie) est exclu. La fenêtre {@code [from, to)} est respectée. Données de test placées
 * en 2099 → aucune collision avec le seed.</p>
 */
class ResourceBookingNoShowStatsIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private static final String FROM = "2099-03-01T00:00:00Z";
    private static final String TO   = "2099-04-01T00:00:00Z";

    private String tenantId(String slug) {
        return jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = ? AND deleted_at IS NULL", String.class, slug);
    }

    /** Un user CLIENT distinct, utilisé comme organisateur isolable (pour des counts déterministes). */
    private String distinctClientId(int skip) {
        return jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id=u.role_id "
            + "WHERE r.code='CLIENT' AND u.deleted_at IS NULL ORDER BY u.id OFFSET ? LIMIT 1",
            String.class, skip);
    }

    private String createResource(String admin, String tenantSlug) throws Exception {
        ResponseEntity<String> rPost = restTemplate.exchange(url("/api/resource-bookings/resources"),
            HttpMethod.POST, jsonJwtEntity(Map.of(
                "tenantId", tenantId(tenantSlug), "resourceType", "padel", "name", "P1.4 Court"), admin), String.class);
        assertThat(rPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return om.readTree(rPost.getBody()).get("id").asText();
    }

    /** Crée un booking (admin → organizer arbitraire) puis le passe au statut donné. */
    private String createBookingWithStatus(String admin, String resourceId, String organizerId,
                                           String startAt, String endAt, String status) throws Exception {
        ResponseEntity<String> bPost = restTemplate.exchange(url("/api/resource-bookings/bookings"),
            HttpMethod.POST, jsonJwtEntity(Map.of(
                "resourceId", resourceId, "organizerId", organizerId,
                "startAt", startAt, "endAt", endAt, "status", "confirmed"), admin), String.class);
        assertThat(bPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bookingId = om.readTree(bPost.getBody()).get("id").asText();
        // PATCH vers le statut final (no_show / completed / cancelled).
        assertThat(restTemplate.exchange(url("/api/resource-bookings/bookings/" + bookingId),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("status", status), admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        return bookingId;
    }

    private void hardClean(String resourceId) {
        jdbc.update("DELETE FROM resource_booking_guests WHERE booking_id IN "
            + "(SELECT id FROM resource_bookings WHERE resource_id = ?::uuid)", resourceId);
        jdbc.update("DELETE FROM resource_bookings WHERE resource_id = ?::uuid", resourceId);
        jdbc.update("DELETE FROM resources WHERE id = ?::uuid", resourceId);
    }

    // ─── RBAC ────────────────────────────────────────────────────────────────────

    @Test
    void noShowStats_noJwt_401() {
        assertThat(restTemplate.exchange(
            url("/api/resource-bookings/no-show-stats?from=" + FROM + "&to=" + TO),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void noShowStats_clientForbidden_403() {
        // CLIENT : pas de VIEW:STAFF (et pas d'usage légitime des stats d'assiduité) → 403.
        assertThat(restTemplate.exchange(
            url("/api/resource-bookings/no-show-stats?from=" + FROM + "&to=" + TO),
            HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void noShowStats_restaurateurOk_reachable200() {
        // RESTAURATEUR : VIEW:BOOKINGS + VIEW:STAFF → 200 (forme tableau).
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/resource-bookings/no-show-stats?from=" + FROM + "&to=" + TO),
            HttpMethod.GET, jwtEntity(bearerForRole("RESTAURATEUR")), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).startsWith("[");
    }

    // ─── Agrégation + scope tenant + fenêtre ──────────────────────────────────────

    @Test
    void noShowStats_aggregatesOwnTenant_excludesForeignTenant_andWindow() throws Exception {
        String admin = adminBearer();
        // Organisateur isolé pour des counts déterministes (oneclick = tenant du RESTAURATEUR).
        String orgLocal = distinctClientId(0);
        String orgForeign = distinctClientId(1);

        String localResource = createResource(admin, "oneclick");
        String foreignResource = createResource(admin, "palmeraie");
        try {
            // Tenant local (oneclick) : 1 no_show + 1 completed + 1 cancelled dans la fenêtre,
            // pour le MÊME organisateur → total 3, honored 1, noShows 1, cancelled 1,
            // taux = 1 / (1 + 1) = 50% (la cancelled hors dénominateur).
            createBookingWithStatus(admin, localResource, orgLocal,
                "2099-03-10T09:00:00Z", "2099-03-10T10:00:00Z", "no_show");
            createBookingWithStatus(admin, localResource, orgLocal,
                "2099-03-11T09:00:00Z", "2099-03-11T10:00:00Z", "completed");
            createBookingWithStatus(admin, localResource, orgLocal,
                "2099-03-12T09:00:00Z", "2099-03-12T10:00:00Z", "cancelled");
            // HORS fenêtre (avril) : ne doit PAS être compté.
            createBookingWithStatus(admin, localResource, orgLocal,
                "2099-04-15T09:00:00Z", "2099-04-15T10:00:00Z", "no_show");

            // Tenant étranger (palmeraie) : un no_show → ne doit JAMAIS apparaître pour le RESTAURATEUR oneclick.
            createBookingWithStatus(admin, foreignResource, orgForeign,
                "2099-03-10T09:00:00Z", "2099-03-10T10:00:00Z", "no_show");

            ResponseEntity<String> resp = restTemplate.exchange(
                url("/api/resource-bookings/no-show-stats?from=" + FROM + "&to=" + TO),
                HttpMethod.GET, jwtEntity(bearerForRole("RESTAURATEUR")), String.class);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode arr = om.readTree(resp.getBody());
            assertThat(arr.isArray()).isTrue();

            JsonNode local = null;
            boolean foreignPresent = false;
            for (JsonNode n : arr) {
                String oid = n.path("organizerId").asText(null);
                if (orgLocal.equals(oid)) local = n;
                if (orgForeign.equals(oid)) foreignPresent = true;
            }
            // L'organisateur étranger (palmeraie) est ABSENT du scope oneclick.
            assertThat(foreignPresent).as("no-show d'un autre tenant exclu du scope").isFalse();

            // L'organisateur local est présent avec les counts attendus (fenêtre mars uniquement).
            assertThat(local).as("organisateur local présent").isNotNull();
            assertThat(local.get("total").asLong()).isEqualTo(3L);
            assertThat(local.get("honored").asLong()).isEqualTo(1L);
            assertThat(local.get("noShows").asLong()).isEqualTo(1L);
            assertThat(local.get("cancelled").asLong()).isEqualTo(1L);
            // Taux = noShows / (honored + noShows) = 1 / 2 = 50% (cancelled hors dénominateur).
            assertThat(local.get("noShowRatePct").asDouble()).isEqualTo(50.0);
            assertThat(local.get("lastNoShowAt").asText()).isEqualTo("2099-03-10T09:00:00Z");
        } finally {
            hardClean(localResource);
            hardClean(foreignResource);
        }
    }
}
