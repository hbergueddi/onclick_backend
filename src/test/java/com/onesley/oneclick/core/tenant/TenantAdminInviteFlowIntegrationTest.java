package com.onesley.oneclick.core.tenant;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration E2 — flux complet invitation tenant-admin (V78).
 *
 * <p>Création/liste/révocation (SUPERADMIN, {@code VERB:TENANTS}) + acceptation publique
 * (lien magique, token SHA-256 single-use). Le token clair n'étant jamais renvoyé par l'API
 * (il ne vit que dans l'email), les tests d'acceptation insèrent l'invitation en base avec un
 * {@code token_hash} calculé en local (même SHA-256 que le service) puis POSTent le token clair.</p>
 */
class TenantAdminInviteFlowIntegrationTest extends AbstractIntegrationTest {

    private UUID anyTenantId() {
        return UUID.fromString(jdbc.queryForObject("SELECT id::text FROM tenants LIMIT 1", String.class));
    }

    private UUID anyRestaurateurId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id WHERE r.code='RESTAURATEUR' LIMIT 1",
            String.class));
    }

    private static String sha256Hex(String raw) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private void insertInvite(UUID id, UUID tenantId, String email, String rawToken, String status, String expiresExpr) {
        jdbc.update("INSERT INTO tenant_admin_invites (id, tenant_id, email, token_hash, tenant_role, status, expires_at) "
            + "VALUES (?::uuid, ?::uuid, ?, ?, 'admin', ?, " + expiresExpr + ")",
            id, tenantId, email, sha256Hex(rawToken), status);
    }

    private int postPublicJson(String path, Map<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, h), String.class)
            .getStatusCode().value();
    }

    private void cleanupUserByEmail(String email) {
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE lower(email)=lower(?))", email);
        jdbc.update("DELETE FROM tenant_admins WHERE user_id IN (SELECT id FROM users WHERE lower(email)=lower(?))", email);
        jdbc.update("DELETE FROM tenant_admin_invites WHERE lower(email)=lower(?)", email);
        jdbc.update("DELETE FROM users WHERE lower(email)=lower(?)", email);
    }

    // ─── Création (SUPERADMIN) ────────────────────────────────────────────────

    @Test
    void createInvite_superadmin_returns201_pendingRow_noTokenLeaked() {
        UUID tenantId = anyTenantId();
        String email = "e2-create-" + UUID.randomUUID() + "@example.com";
        try {
            var resp = restTemplate.exchange(url("/api/tenants/" + tenantId + "/admin-invites"),
                HttpMethod.POST, jsonJwtEntity(Map.of("email", email, "role", "admin"), adminBearer()), String.class);
            assertThat(resp.getStatusCode().value()).isEqualTo(201);
            assertThat(resp.getBody()).doesNotContain("token"); // jamais de token dans la réponse

            Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_admin_invites WHERE lower(email)=lower(?) AND status='pending'",
                Integer.class, email);
            assertThat(pending).isEqualTo(1);
        } finally {
            jdbc.update("DELETE FROM tenant_admin_invites WHERE lower(email)=lower(?)", email);
        }
    }

    @Test
    void createInvite_restaurateur_returns403() {
        UUID tenantId = anyTenantId();
        String jwt = jwtIssuer.issueAccessToken(anyRestaurateurId(), "RESTAURATEUR").token();
        int status = restTemplate.exchange(url("/api/tenants/" + tenantId + "/admin-invites"),
            HttpMethod.POST, jsonJwtEntity(Map.of("email", "x@y.ma", "role", "admin"), jwt), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void listInvites_superadmin_returns200() {
        UUID tenantId = anyTenantId();
        int status = restTemplate.exchange(url("/api/tenants/" + tenantId + "/admin-invites"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode().value();
        assertThat(status).isEqualTo(200);
    }

    // ─── Acceptation publique (lien magique) ──────────────────────────────────

    @Test
    void accept_validToken_creates_user_assigns_admin_and_redeems() {
        UUID tenantId = anyTenantId();
        UUID inviteId = UUID.randomUUID();
        String email = "e2-accept-" + UUID.randomUUID() + "@example.com";
        String raw = "tok-" + UUID.randomUUID();
        try {
            insertInvite(inviteId, tenantId, email, raw, "pending", "now() + interval '7 days'");

            int status = postPublicJson("/api/auth/accept-tenant-admin-invite",
                Map.of("token", raw, "password", "Passw0rd!1", "firstName", "Adil", "lastName", "Test"));
            assertThat(status).isEqualTo(200);

            UUID userId = UUID.fromString(jdbc.queryForObject(
                "SELECT id::text FROM users WHERE lower(email)=lower(?)", String.class, email));
            Integer adminRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_admins WHERE tenant_id=?::uuid AND user_id=?::uuid",
                Integer.class, tenantId, userId);
            assertThat(adminRows).isEqualTo(1);
            String invStatus = jdbc.queryForObject(
                "SELECT status FROM tenant_admin_invites WHERE id=?::uuid", String.class, inviteId);
            assertThat(invStatus).isEqualTo("accepted");
        } finally {
            cleanupUserByEmail(email);
        }
    }

    @Test
    void accept_alreadyAcceptedToken_returns400() {
        UUID tenantId = anyTenantId();
        UUID inviteId = UUID.randomUUID();
        String email = "e2-used-" + UUID.randomUUID() + "@example.com";
        String raw = "tok-" + UUID.randomUUID();
        try {
            insertInvite(inviteId, tenantId, email, raw, "accepted", "now() + interval '7 days'");
            int status = postPublicJson("/api/auth/accept-tenant-admin-invite",
                Map.of("token", raw, "password", "Passw0rd!1", "firstName", "A", "lastName", "B"));
            assertThat(status).isEqualTo(400);
        } finally {
            jdbc.update("DELETE FROM tenant_admin_invites WHERE id=?::uuid", inviteId);
        }
    }

    @Test
    void accept_expiredToken_returns400() {
        UUID tenantId = anyTenantId();
        UUID inviteId = UUID.randomUUID();
        String email = "e2-exp-" + UUID.randomUUID() + "@example.com";
        String raw = "tok-" + UUID.randomUUID();
        try {
            insertInvite(inviteId, tenantId, email, raw, "pending", "now() - interval '1 day'");
            int status = postPublicJson("/api/auth/accept-tenant-admin-invite",
                Map.of("token", raw, "password", "Passw0rd!1", "firstName", "A", "lastName", "B"));
            assertThat(status).isEqualTo(400);
        } finally {
            jdbc.update("DELETE FROM tenant_admin_invites WHERE id=?::uuid", inviteId);
        }
    }

    @Test
    void accept_invalidToken_returns400() {
        int status = postPublicJson("/api/auth/accept-tenant-admin-invite",
            Map.of("token", "does-not-exist-" + UUID.randomUUID(), "password", "Passw0rd!1",
                "firstName", "A", "lastName", "B"));
        assertThat(status).isEqualTo(400);
    }

    // ─── Révocation (SUPERADMIN) ──────────────────────────────────────────────

    @Test
    void revokeInvite_superadmin_returns204_andStatusRevoked() {
        UUID tenantId = anyTenantId();
        UUID inviteId = UUID.randomUUID();
        String email = "e2-rev-" + UUID.randomUUID() + "@example.com";
        try {
            insertInvite(inviteId, tenantId, email, "tok-" + UUID.randomUUID(), "pending", "now() + interval '7 days'");
            int status = restTemplate.exchange(
                url("/api/tenants/" + tenantId + "/admin-invites/" + inviteId),
                HttpMethod.DELETE, jwtEntity(adminBearer()), String.class).getStatusCode().value();
            assertThat(status).isEqualTo(204);
            String invStatus = jdbc.queryForObject(
                "SELECT status FROM tenant_admin_invites WHERE id=?::uuid", String.class, inviteId);
            assertThat(invStatus).isEqualTo("revoked");
        } finally {
            jdbc.update("DELETE FROM tenant_admin_invites WHERE id=?::uuid", inviteId);
        }
    }
}
