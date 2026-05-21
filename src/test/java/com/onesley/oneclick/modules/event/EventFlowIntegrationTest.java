package com.onesley.oneclick.modules.event;

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
 * L4 « profondeur » — flux end-to-end {@code /api/events}.
 * Event CRUD + RSVP (participations) + listes elite + erreurs.
 */
class EventFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private String tenantId() {
        return jdbc.queryForObject("SELECT tenant_id::text FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1", String.class);
    }
    private String userId() {
        return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class);
    }

    @Test
    void event_fullLifecycle_withRsvp() throws Exception {
        String admin = adminBearer();

        // CREATE
        ResponseEntity<String> post = restTemplate.exchange(url("/api/events"), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "tenantId", tenantId(), "title", "L4 Event",
                "eventAt", Instant.now().plus(7, ChronoUnit.DAYS).toString(),
                "capacity", 100, "isActive", true), admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = om.readTree(post.getBody()).get("id").asText();

        // GET + list + PATCH
        assertThat(restTemplate.exchange(url("/api/events/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/events?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> patch = restTemplate.exchange(url("/api/events/" + id), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("title", "L4 Event Patched"), admin), String.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(patch.getBody()).get("title").asText()).isEqualTo("L4 Event Patched");

        // RSVP
        String uid = userId();
        assertThat(restTemplate.exchange(url("/api/events/participations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("eventId", id, "userId", uid, "status", "going"), admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(restTemplate.exchange(url("/api/events/" + id + "/participations"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/events/participations/by-user/" + uid), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/events/participations/by-event/" + id + "/user/" + uid),
            HttpMethod.DELETE, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // elite lists
        assertThat(restTemplate.exchange(url("/api/events/elite/active"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/events/elite/all"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // DELETE → 404 (self-clean)
        assertThat(restTemplate.exchange(url("/api/events/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.exchange(url("/api/events/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void event_unknownId_404() {
        assertThat(restTemplate.exchange(url("/api/events/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createEvent_invalidBody_400() {
        assertThat(restTemplate.exchange(url("/api/events"), HttpMethod.POST,
            jsonJwtEntity(Map.of("description", "sans titre ni date"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createEvent_asClient_403() {
        assertThat(restTemplate.exchange(url("/api/events"), HttpMethod.POST,
            jsonJwtEntity(Map.of("tenantId", tenantId(), "title", "X",
                "eventAt", Instant.now().plus(1, ChronoUnit.DAYS).toString()), bearerForRole("CLIENT")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
