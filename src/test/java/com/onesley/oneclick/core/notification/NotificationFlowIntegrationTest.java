package com.onesley.oneclick.core.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/notifications} : notifs, cloche, campaigns, tokens, push. */
class NotificationFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String userId() { return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class); }
    private String tenantId() { return jdbc.queryForObject("SELECT tenant_id::text FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1", String.class); }

    @Test
    void notification_fullFlow() throws Exception {
        String admin = adminBearer();
        String uid = userId();

        ResponseEntity<String> post = restTemplate.exchange(url("/api/notifications"), HttpMethod.POST,
            jsonJwtEntity(Map.of("recipientUserId", uid, "type", "system", "title", "L4", "body", "corps L4"), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();

        assertThat(restTemplate.exchange(url("/api/notifications?recipientUserId=" + uid + "&page=0&size=5"),
            HttpMethod.GET, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/notifications/by-user/" + uid), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/notifications/unread-count/by-user/" + uid), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/notifications/" + id + "/read"), HttpMethod.PATCH, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/notifications/mark-all-read/by-user/" + uid), HttpMethod.PATCH, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // campaigns
        ResponseEntity<String> camp = restTemplate.exchange(url("/api/notifications/campaigns"), HttpMethod.POST,
            jsonJwtEntity(Map.of("tenantId", tenantId(), "title", "Camp L4", "message", "msg"), admin), String.class);
        assertThat(camp.getStatusCode().is2xxSuccessful()).isTrue();
        String campId = om.readTree(camp.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/notifications/campaigns/by-tenant/" + tenantId()), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // tokens
        ResponseEntity<String> tok = restTemplate.exchange(url("/api/notifications/tokens"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", uid, "token", "l4-" + UUID.randomUUID(), "platform", "ios"), admin), String.class);
        assertThat(tok.getStatusCode().is2xxSuccessful()).isTrue();
        String tokenId = om.readTree(tok.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/notifications/tokens/by-user/" + uid), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/notifications/tokens/" + tokenId), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // push (stub FCM → 200)
        assertThat(restTemplate.exchange(url("/api/notifications/push/promo"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userIds", List.of(uid), "title", "T", "body", "B"), admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(restTemplate.exchange(url("/api/notifications/push/reservation"), HttpMethod.POST,
            jsonJwtEntity(Map.of("reservationId", UUID.randomUUID().toString(), "recipientUserId", uid,
                "status", "confirmed", "title", "T", "body", "B"), admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();

        // self-clean (notification + campaign n'ont pas d'endpoint DELETE)
        jdbc.update("DELETE FROM notifications WHERE id = ?::uuid", UUID.fromString(id));
        jdbc.update("DELETE FROM notification_campaigns WHERE id = ?::uuid", UUID.fromString(campId));
    }

    @Test
    void markRead_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/notifications/" + UUID.randomUUID() + "/read"),
            HttpMethod.PATCH, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_invalidBody_400() {
        assertThat(restTemplate.exchange(url("/api/notifications"), HttpMethod.POST,
            jsonJwtEntity(Map.of("title", "sans destinataire ni type"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/notifications?page=0&size=5"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
