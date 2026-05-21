package com.onesley.oneclick.modules.resource_booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L4 « profondeur » — flux end-to-end {@code /api/resource-bookings}.
 * Resources → pricings → bookings → guests : cycle complet self-clean + erreurs.
 */
class ResourceBookingFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private String tenantId() {
        return jdbc.queryForObject("SELECT tenant_id::text FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1", String.class);
    }
    private String userId() {
        return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class);
    }

    @Test
    void resourceBooking_fullLifecycle() throws Exception {
        String admin = adminBearer();

        // CREATE resource
        ResponseEntity<String> rPost = restTemplate.exchange(url("/api/resource-bookings/resources"),
            HttpMethod.POST, jsonJwtEntity(Map.of(
                "tenantId", tenantId(), "resourceType", "padel", "name", "L4 Court", "capacity", 4), admin), String.class);
        assertThat(rPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String resourceId = om.readTree(rPost.getBody()).get("id").asText();

        // GET resource + list
        assertThat(restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId),
            HttpMethod.GET, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/resource-bookings/resources?page=0&size=5"),
            HttpMethod.GET, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // CREATE pricing + list
        ResponseEntity<String> pPost = restTemplate.exchange(url("/api/resource-bookings/pricings"),
            HttpMethod.POST, jsonJwtEntity(Map.of(
                "resourceId", resourceId, "name", "90min", "price", 200, "durationMinutes", 90), admin), String.class);
        assertThat(pPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId + "/pricings"),
            HttpMethod.GET, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // CREATE booking
        ResponseEntity<String> bPost = restTemplate.exchange(url("/api/resource-bookings/bookings"),
            HttpMethod.POST, jsonJwtEntity(Map.of(
                "resourceId", resourceId, "organizerId", userId(),
                "startAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                "endAt", Instant.now().plus(1, ChronoUnit.DAYS).plus(90, ChronoUnit.MINUTES).toString(),
                "status", "confirmed"), admin), String.class);
        assertThat(bPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bookingId = om.readTree(bPost.getBody()).get("id").asText();

        // GET booking + list + PATCH
        assertThat(restTemplate.exchange(url("/api/resource-bookings/bookings/" + bookingId),
            HttpMethod.GET, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/resource-bookings/bookings?page=0&size=5"),
            HttpMethod.GET, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> bPatch = restTemplate.exchange(url("/api/resource-bookings/bookings/" + bookingId),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("status", "cancelled"), admin), String.class);
        assertThat(bPatch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(bPatch.getBody()).get("status").asText()).isEqualTo("cancelled");

        // GUESTS
        assertThat(restTemplate.exchange(url("/api/resource-bookings/guests"),
            HttpMethod.POST, jsonJwtEntity(Map.of("bookingId", bookingId, "guestName", "Invité L4"), admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(restTemplate.exchange(url("/api/resource-bookings/bookings/" + bookingId + "/guests"),
            HttpMethod.GET, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // DELETE booking → 404, DELETE resource → 404 (self-clean)
        assertThat(restTemplate.exchange(url("/api/resource-bookings/bookings/" + bookingId),
            HttpMethod.DELETE, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.exchange(url("/api/resource-bookings/bookings/" + bookingId),
            HttpMethod.GET, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId),
            HttpMethod.DELETE, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId),
            HttpMethod.GET, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void resource_unknownId_404() {
        assertThat(restTemplate.exchange(url("/api/resource-bookings/resources/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createResource_invalidBody_400() {
        assertThat(restTemplate.exchange(url("/api/resource-bookings/resources"),
            HttpMethod.POST, jsonJwtEntity(Map.of("resourceType", "padel"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createResource_asClient_403() {
        assertThat(restTemplate.exchange(url("/api/resource-bookings/resources"),
            HttpMethod.POST, jsonJwtEntity(Map.of("tenantId", tenantId(), "resourceType", "padel", "name", "X"),
                bearerForRole("CLIENT")), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
