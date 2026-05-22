package com.onesley.oneclick.core.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/notifications/promo-requests} : create/review/sent/stats. */
class PromoNotificationFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String tenantId() { return jdbc.queryForObject("SELECT tenant_id::text FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1", String.class); }
    private String restaurantId() { return jdbc.queryForObject("SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class); }
    private String userId() { return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void promoRequest_fullFlow() throws Exception {
        String admin = adminBearer();
        ResponseEntity<String> post = restTemplate.exchange(url("/api/notifications/promo-requests"), HttpMethod.POST,
            jsonJwtEntity(Map.of("tenantId", tenantId(), "restaurantId", restaurantId(),
                "title", "Promo L4", "body", "corps", "segment", "all", "requestedBy", userId()), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();

        assertThat(restTemplate.exchange(url("/api/notifications/promo-requests"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/notifications/promo-requests/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> review = restTemplate.exchange(url("/api/notifications/promo-requests/" + id + "/review"),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("status", "approved", "reviewedBy", userId()), admin), String.class);
        assertThat(review.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(review.getBody()).get("status").asText()).isEqualTo("approved");

        assertThat(restTemplate.exchange(url("/api/notifications/promo-requests/" + id + "/sent?sentCount=10"),
            HttpMethod.PATCH, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/notifications/promo-requests/stats"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbc.update("DELETE FROM promo_notification_requests WHERE id = ?::uuid", UUID.fromString(id)); // self-clean
    }

    @Test
    void getById_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/notifications/promo-requests/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/notifications/promo-requests"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
