package com.onesley.oneclick.modules.membercircle;

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
 * RBAC/intégration L4 — « Circle » modération posts membres (C4.8c, ressource {@code TENANTS}).
 *
 * <p>Stack réelle : SUPERADMIN → 200 (list) + flux approve/reject/delete persistés en DB ;
 * rôle sans autorité TENANTS → 403 ; sans JWT → 401 ; id inconnu → 404.</p>
 */
class MemberCircleIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private UUID palmeraieTenantId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = 'palmeraie'", String.class));
    }

    private UUID anyUserId() {
        return UUID.fromString(jdbc.queryForObject("SELECT id::text FROM users LIMIT 1", String.class));
    }

    private UUID insertPost(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO member_posts (id, tenant_id, author_id, content, status)
            VALUES (?, ?, ?, ?, ?)
            """, id, palmeraieTenantId(), anyUserId(), "Post test modération", status);
        return id;
    }

    @Test
    void list_admin_returns200_withShape() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/member-posts?tenantId=" + palmeraieTenantId()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode())
            .as("reçu %s, body=%s", resp.getStatusCode(), resp.getBody())
            .isEqualTo(HttpStatus.OK);
        JsonNode body = om.readTree(resp.getBody());
        assertThat(body.get("posts").isArray()).isTrue();
        assertThat(body.get("summary").has("pending")).isTrue();
    }

    @Test
    void list_nonSuperAdmin_forbidden() {
        assertThat(restTemplate.exchange(
            url("/api/member-posts?tenantId=" + palmeraieTenantId()),
            HttpMethod.GET, jwtEntity(bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void list_noJwt_unauthorized() {
        assertThat(restTemplate.exchange(
            url("/api/member-posts?tenantId=" + palmeraieTenantId()),
            HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void approve_setsApprovedStatus() {
        UUID id = insertPost("pending");
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/member-posts/" + id + "/approve"),
            HttpMethod.PATCH, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String status = jdbc.queryForObject("SELECT status FROM member_posts WHERE id = ?", String.class, id);
        assertThat(status).isEqualTo("approved");
    }

    @Test
    void reject_setsRejectedWithReason() {
        UUID id = insertPost("pending");
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/member-posts/" + id + "/reject"),
            HttpMethod.PATCH, jsonJwtEntity("{\"reason\":\"hors charte\"}", adminBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String status = jdbc.queryForObject("SELECT status FROM member_posts WHERE id = ?", String.class, id);
        String reason = jdbc.queryForObject("SELECT rejection_reason FROM member_posts WHERE id = ?", String.class, id);
        assertThat(status).isEqualTo("rejected");
        assertThat(reason).isEqualTo("hors charte");
    }

    @Test
    void delete_softDeletes() {
        UUID id = insertPost("pending");
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/member-posts/" + id),
            HttpMethod.DELETE, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        Integer deleted = jdbc.queryForObject(
            "SELECT COUNT(*) FROM member_posts WHERE id = ? AND deleted_at IS NOT NULL", Integer.class, id);
        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void approve_unknownId_notFound() {
        assertThat(restTemplate.exchange(
            url("/api/member-posts/" + UUID.randomUUID() + "/approve"),
            HttpMethod.PATCH, jwtEntity(adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
