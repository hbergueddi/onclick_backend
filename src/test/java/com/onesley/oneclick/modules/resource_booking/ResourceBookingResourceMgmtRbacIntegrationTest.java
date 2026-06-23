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
 * P1.3 (Lot A) — édition partielle + toggle d'une bookable resource (gestion du parc).
 *
 * <p>Invariant RBAC : {@code PATCH /resources/{id}} et {@code PATCH /resources/{id}/enabled} sont
 * gardés {@code UPDATE:RESOURCE_BOOKINGS} (gestion parc) → un CLIENT, qui ne détient que
 * {@code VIEW:RESOURCE_BOOKINGS} (découverte), est refusé <b>403</b> ; le RESTAURATEUR (qui détient
 * {@code UPDATE:RESOURCE_BOOKINGS}) obtient <b>200</b>. Couvre aussi le patch partiel (COALESCE),
 * le round-trip enabled true→false→true et le 409 sur delete-avec-réservations-actives.</p>
 */
class ResourceBookingResourceMgmtRbacIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    /** Tenant des ressources de test = oneclick (le tenant des RESTAURATEUR/CLIENT seedés). */
    private String tenantId() {
        return jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = 'oneclick' AND deleted_at IS NULL", String.class);
    }

    /** Crée une ressource (parc) via l'admin et renvoie son id. */
    private String createResourceAsAdmin(String admin) throws Exception {
        ResponseEntity<String> rPost = restTemplate.exchange(url("/api/resource-bookings/resources"),
            HttpMethod.POST, jsonJwtEntity(Map.of(
                "tenantId", tenantId(), "resourceType", "padel", "name", "P1.3 Court",
                "capacity", 4, "slotDurationMinutes", 60, "maxInvitees", 3), admin), String.class);
        assertThat(rPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return om.readTree(rPost.getBody()).get("id").asText();
    }

    private void hardClean(String resourceId) {
        jdbc.update("DELETE FROM resource_booking_guests WHERE booking_id IN "
            + "(SELECT id FROM resource_bookings WHERE resource_id = ?::uuid)", resourceId);
        jdbc.update("DELETE FROM resource_bookings WHERE resource_id = ?::uuid", resourceId);
        jdbc.update("DELETE FROM resources WHERE id = ?::uuid", resourceId);
    }

    // ─── A. RBAC : 403 sans UPDATE:RESOURCE_BOOKINGS, 200 pour RESTAURATEUR ────────

    @Test
    void patchResource_clientForbidden_restaurateurOk() throws Exception {
        String admin = adminBearer();
        String resourceId = createResourceAsAdmin(admin);
        try {
            // CLIENT (VIEW:RESOURCE_BOOKINGS seul) → 403
            assertThat(restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId),
                HttpMethod.PATCH, jsonJwtEntity(Map.of("name", "Hack"), bearerForRole("CLIENT")), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            // RESTAURATEUR (UPDATE:RESOURCE_BOOKINGS) → 200 + patch partiel appliqué
            ResponseEntity<String> resto = restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId),
                HttpMethod.PATCH, jsonJwtEntity(Map.of("name", "Court Renommé", "slotDurationMinutes", 90),
                    bearerForRole("RESTAURATEUR")), String.class);
            assertThat(resto.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode body = om.readTree(resto.getBody());
            assertThat(body.get("name").asText()).isEqualTo("Court Renommé");
            assertThat(body.get("slotDurationMinutes").asInt()).isEqualTo(90);
            // COALESCE : capacity/maxInvitees non fournis → inchangés.
            assertThat(body.get("capacity").asInt()).isEqualTo(4);
            assertThat(body.get("maxInvitees").asInt()).isEqualTo(3);
            // Type verrouillé.
            assertThat(body.get("resourceType").asText()).isEqualTo("padel");
        } finally {
            hardClean(resourceId);
        }
    }

    @Test
    void patchEnabled_clientForbidden_roundTripTrueFalseTrue() throws Exception {
        String admin = adminBearer();
        String resourceId = createResourceAsAdmin(admin);
        try {
            // CLIENT → 403
            assertThat(restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId + "/enabled"),
                HttpMethod.PATCH, jsonJwtEntity(Map.of("enabled", false), bearerForRole("CLIENT")), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

            // RESTAURATEUR : true → false
            ResponseEntity<String> off = restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId + "/enabled"),
                HttpMethod.PATCH, jsonJwtEntity(Map.of("enabled", false), bearerForRole("RESTAURATEUR")), String.class);
            assertThat(off.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(om.readTree(off.getBody()).get("enabled").asBoolean()).isFalse();

            // false → true
            ResponseEntity<String> on = restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId + "/enabled"),
                HttpMethod.PATCH, jsonJwtEntity(Map.of("enabled", true), bearerForRole("RESTAURATEUR")), String.class);
            assertThat(on.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(om.readTree(on.getBody()).get("enabled").asBoolean()).isTrue();
        } finally {
            hardClean(resourceId);
        }
    }

    // ─── B. Validation métier (422) + 404 ─────────────────────────────────────────

    @Test
    void patchResource_invalidValues_422() throws Exception {
        String admin = adminBearer();
        String resourceId = createResourceAsAdmin(admin);
        try {
            // slotDurationMinutes < 15 → rejet (validation DTO @Min(15) → 400 ; borne structurelle).
            assertThat(restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId),
                HttpMethod.PATCH, jsonJwtEntity(Map.of("slotDurationMinutes", 5), admin), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        } finally {
            hardClean(resourceId);
        }
    }

    @Test
    void patchResource_unknownId_404() {
        assertThat(restTemplate.exchange(url("/api/resource-bookings/resources/" + java.util.UUID.randomUUID()),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("name", "X"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─── C. 409 sur delete-avec-réservations-actives ──────────────────────────────

    @Test
    void deleteResource_withActiveBooking_409_thenOkAfterPurge() throws Exception {
        String admin = adminBearer();
        String resourceId = createResourceAsAdmin(admin);
        // Booking actif (admin → organizer arbitraire) sur la ressource.
        ResponseEntity<String> bPost = restTemplate.exchange(url("/api/resource-bookings/bookings"),
            HttpMethod.POST, jsonJwtEntity(Map.of(
                "resourceId", resourceId,
                "organizerId", jdbc.queryForObject(
                    "SELECT u.id::text FROM users u JOIN roles r ON r.id=u.role_id "
                    + "WHERE r.code='CLIENT' AND u.deleted_at IS NULL ORDER BY u.id LIMIT 1", String.class),
                "startAt", "2099-01-01T10:00:00Z",
                "endAt", "2099-01-01T11:00:00Z",
                "status", "confirmed"), admin), String.class);
        assertThat(bPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bookingId = om.readTree(bPost.getBody()).get("id").asText();

        try {
            // DELETE refusé (409) tant qu'une réservation vit.
            assertThat(restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId),
                HttpMethod.DELETE, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

            // Après soft-delete de la réservation, le DELETE passe (204).
            assertThat(restTemplate.exchange(url("/api/resource-bookings/bookings/" + bookingId),
                HttpMethod.DELETE, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId),
                HttpMethod.DELETE, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        } finally {
            hardClean(resourceId); // défensif (idempotent)
        }
    }

    @Test
    void patchResource_noJwt_401() {
        assertThat(restTemplate.exchange(url("/api/resource-bookings/resources/" + java.util.UUID.randomUUID()),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("name", "X"), null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
