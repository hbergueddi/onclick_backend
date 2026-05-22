package com.onesley.oneclick.modules.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/reservations} : lifecycle + status + booking-rules + guests. */
class ReservationFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String[] restoTenant() {
        return jdbc.queryForObject(
            "SELECT r.id::text || ',' || r.tenant_id::text FROM restaurants r WHERE r.tenant_id IS NOT NULL AND r.deleted_at IS NULL LIMIT 1",
            String.class).split(",");
    }
    private String userId() { return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void reservation_fullLifecycle() throws Exception {
        String admin = adminBearer();
        String[] rt = restoTenant();
        String restaurantId = rt[0], tenantId = rt[1], uid = userId();

        ResponseEntity<String> post = restTemplate.exchange(url("/api/reservations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("tenantId", tenantId, "clientId", uid, "restaurantId", restaurantId,
                "reservationAt", Instant.now().plus(3, ChronoUnit.DAYS).toString(), "guestCount", 2, "notes", "L4"), admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = om.readTree(post.getBody()).get("id").asText();

        assertThat(restTemplate.exchange(url("/api/reservations/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/reservations?restaurantId=" + restaurantId + "&page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/reservations/batch"), HttpMethod.POST,
            jsonJwtEntity(List.of(id), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/reservations/search"), HttpMethod.POST,
            jsonJwtEntity(Map.of("criteria", List.of(), "page", 0, "size", 5), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/reservations/count-by-restaurant?sinceDays=30"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // status transition pending → confirmed
        ResponseEntity<String> status = restTemplate.exchange(url("/api/reservations/" + id + "/status"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "confirmed", "changedById", uid, "reason", "L4 confirm"), admin), String.class);
        assertThat(status.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(status.getBody()).get("status").asText()).isEqualTo("confirmed");

        // guests
        ResponseEntity<String> gPost = restTemplate.exchange(url("/api/reservations/" + id + "/guests"), HttpMethod.POST,
            jsonJwtEntity(Map.of("guestName", "Invité L4", "status", "invited"), admin), String.class);
        assertThat(gPost.getStatusCode().is2xxSuccessful()).isTrue();
        String guestId = om.readTree(gPost.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/reservations/" + id + "/guests"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/reservations/guests/by-user/" + uid), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/reservations/guests/" + guestId + "/status"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "accepted"), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/reservations/" + id + "/guests/mark-seen"), HttpMethod.PATCH, jwtEntity(admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(restTemplate.exchange(url("/api/reservations/guests/" + guestId), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();

        // self-clean : annulation
        restTemplate.exchange(url("/api/reservations/" + id + "/status"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "cancelled", "changedById", uid, "reason", "L4 cleanup"), admin), String.class);
    }

    @Test
    void bookingRules_crud() throws Exception {
        String admin = adminBearer();
        String restaurantId = restoTenant()[0];
        assertThat(restTemplate.exchange(url("/api/reservations/booking-rules/by-restaurant/" + restaurantId), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> post = restTemplate.exchange(url("/api/reservations/booking-rules/by-restaurant/" + restaurantId), HttpMethod.POST,
            jsonJwtEntity(Map.of("maxGuest", 8, "slotDuration", 90, "cancellationWindowHours", 2), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String ruleId = om.readTree(post.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/reservations/booking-rules/" + ruleId), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("maxGuest", 10), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/reservations/booking-rules/" + ruleId), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void reservation_unknownId_404() {
        assertThat(restTemplate.exchange(url("/api/reservations/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_invalidBody_400() {
        assertThat(restTemplate.exchange(url("/api/reservations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("notes", "sans FK ni date"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void list_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/reservations?page=0&size=5"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
