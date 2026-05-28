package com.onesley.oneclick.modules.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/support} : tickets + messages + attachments + update. */
class SupportFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String userId() { return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void support_fullFlow() throws Exception {
        String admin = adminBearer();
        String uid = userId();

        ResponseEntity<String> post = restTemplate.exchange(url("/api/support/tickets"), HttpMethod.POST,
            jsonJwtEntity(Map.of("openedById", uid, "category", "billing", "subject", "Ticket L4",
                "priority", "high", "message", "corps L4"), admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = om.readTree(post.getBody()).get("id").asText();

        assertThat(restTemplate.exchange(url("/api/support/tickets?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/support/tickets/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // UPDATE (+ escalade vers admin — TicketUpdateDto.escalatedToAdmin)
        ResponseEntity<String> upd = restTemplate.exchange(url("/api/support/tickets/" + id), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "in_progress", "priority", "urgent", "escalatedToAdmin", true), admin), String.class);
        assertThat(upd.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(upd.getBody()).get("status").asText()).isEqualTo("in_progress");
        assertThat(om.readTree(upd.getBody()).get("escalatedToAdmin").asBoolean()).isTrue();

        // messages
        assertThat(restTemplate.exchange(url("/api/support/messages"), HttpMethod.POST,
            jsonJwtEntity(Map.of("ticketId", id, "authorId", uid, "message", "réponse L4"), admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(restTemplate.exchange(url("/api/support/tickets/" + id + "/messages"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // attachments
        assertThat(restTemplate.exchange(url("/api/support/attachments"), HttpMethod.POST,
            jsonJwtEntity(Map.of("ticketId", id, "url", "https://x/doc.pdf", "fileName", "doc.pdf", "mimeType", "application/pdf"), admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(restTemplate.exchange(url("/api/support/tickets/" + id + "/attachments"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getTicket_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/support/tickets/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createTicket_invalidBody_400() {
        assertThat(restTemplate.exchange(url("/api/support/tickets"), HttpMethod.POST,
            jsonJwtEntity(Map.of("category", "billing"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/support/tickets?page=0&size=5"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
