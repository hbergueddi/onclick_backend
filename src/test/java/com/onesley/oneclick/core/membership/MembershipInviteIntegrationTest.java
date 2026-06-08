package com.onesley.oneclick.core.membership;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration P2 — {@code POST /api/tenants/{tenantId}/members} (invitation par l'admin du tenant).
 *
 * <p>Vérifie la matrice de sécurité (RBAC {@code CREATE:MEMBERSHIPS} + ABAC own-tenant) et le flux
 * métier (réutilise/crée le compte OneClick unique + membership active + pliage des autorités).
 * DB réelle {@code oneclick_enterprise}. ⚠️ Cache {@code userDetails} à flusher avant exécution
 * (changement de matrice RBAC V94) — sinon faux 403 au {@code @PreAuthorize}.</p>
 */
class MembershipInviteIntegrationTest extends AbstractIntegrationTest {

    private static final String MEMBER_ROLE_ID = "10000000-0000-0000-0000-000000000006";

    private final ObjectMapper om = new ObjectMapper();

    @Autowired private MembershipDirectoryApi membershipDirectory;

    private UUID tenantIdBySlug(String slug) {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = ?", String.class, slug));
    }

    /** Bearer d'un admin de tenant (RESTAURATEUR home=slug) — les admins PCC/HOMU sont RESTAURATEUR. */
    private String tenantAdminBearer(String slug) {
        String id = jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id JOIN tenants t ON t.id = u.tenant_id "
            + "WHERE r.code = 'RESTAURATEUR' AND t.slug = ? AND u.deleted_at IS NULL ORDER BY u.id LIMIT 1",
            String.class, slug);
        return jwtIssuer.issueAccessToken(UUID.fromString(id), "RESTAURATEUR").token();
    }

    private String clientBearer() {
        String id = jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL ORDER BY u.id LIMIT 1", String.class);
        return jwtIssuer.issueAccessToken(UUID.fromString(id), "CLIENT").token();
    }

    private Map<String, Object> body(String email, String firstName, String lastName) {
        return Map.of("email", email, "firstName", firstName, "lastName", lastName);
    }

    /** Nettoyage doux : retire la membership + soft-delete le compte créé (FK-safe vs invite token async). */
    private void cleanupCreatedUser(String userId) {
        jdbc.update("DELETE FROM tenant_memberships WHERE user_id = ?::uuid", userId);
        jdbc.update("UPDATE users SET deleted_at = now() WHERE id = ?::uuid", userId);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@invite.test";
    }

    // ─── FLUX MÉTIER ────────────────────────────────────────────────────────

    @Test
    void palmeraieAdmin_invitesNewMember_201_activeMembership_MEMBER_role_foldsAuthorities() throws Exception {
        UUID palmeraie = tenantIdBySlug("palmeraie");
        String email = uniqueEmail("new");

        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/tenants/" + palmeraie + "/members"), HttpMethod.POST,
            jsonJwtEntity(body(email, "New", "Member"), tenantAdminBearer("palmeraie")), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var json = om.readTree(resp.getBody());
        assertThat(json.get("newAccount").asBoolean()).isTrue();
        assertThat(json.get("status").asText()).isEqualTo("active");
        UUID userId = UUID.fromString(json.get("userId").asText());

        // Compte créé en tenant home oneclick (modèle un-seul-compte).
        String homeSlug = jdbc.queryForObject(
            "SELECT t.slug FROM users u JOIN tenants t ON t.id = u.tenant_id WHERE u.id = ?::uuid",
            String.class, userId.toString());
        assertThat(homeSlug).isEqualTo("oneclick");

        // Membership active, rôle MEMBER, dans palmeraie.
        String roleId = jdbc.queryForObject(
            "SELECT role_id::text FROM tenant_memberships "
            + "WHERE user_id = ?::uuid AND tenant_id = ?::uuid AND deleted_at IS NULL",
            String.class, userId.toString(), palmeraie.toString());
        assertThat(roleId).isEqualTo(MEMBER_ROLE_ID);

        // Pliage des autorités : le membre détient les autorités programme.
        assertThat(membershipDirectory.authoritiesFor(userId)).contains("CREATE:BOOKINGS", "VIEW:FAMILY");

        cleanupCreatedUser(userId.toString());
    }

    @Test
    void superAdmin_invitesIntoAnyTenant_201() throws Exception {
        UUID palmeraie = tenantIdBySlug("palmeraie");
        String email = uniqueEmail("sa");
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/tenants/" + palmeraie + "/members"), HttpMethod.POST,
            jsonJwtEntity(body(email, "Sa", "Member"), adminBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        cleanupCreatedUser(om.readTree(resp.getBody()).get("userId").asText());
    }

    @Test
    void existingAccount_reused_newAccountFalse_andIdempotentSecondInvite() throws Exception {
        UUID palmeraie = tenantIdBySlug("palmeraie");
        // Un CLIENT oneclick existant (compte unique réutilisé par email).
        String existingId = jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id JOIN tenants t ON t.id = u.tenant_id "
            + "WHERE r.code = 'CLIENT' AND t.slug = 'oneclick' AND u.email IS NOT NULL AND u.deleted_at IS NULL "
            + "ORDER BY u.id LIMIT 1", String.class);
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?::uuid", String.class, existingId);
        jdbc.update("DELETE FROM tenant_memberships WHERE user_id = ?::uuid AND tenant_id = ?::uuid",
            existingId, palmeraie.toString());

        ResponseEntity<String> r1 = restTemplate.exchange(
            url("/api/tenants/" + palmeraie + "/members"), HttpMethod.POST,
            jsonJwtEntity(body(email, "Ignored", "Ignored"), adminBearer()), String.class);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var j1 = om.readTree(r1.getBody());
        assertThat(j1.get("newAccount").asBoolean()).isFalse();
        assertThat(j1.get("userId").asText()).isEqualTo(existingId);
        String membershipId = j1.get("id").asText();

        // Re-invite → idempotent : même membership, pas de doublon.
        ResponseEntity<String> r2 = restTemplate.exchange(
            url("/api/tenants/" + palmeraie + "/members"), HttpMethod.POST,
            jsonJwtEntity(body(email, "Ignored", "Ignored"), adminBearer()), String.class);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(om.readTree(r2.getBody()).get("id").asText()).isEqualTo(membershipId);

        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM tenant_memberships "
            + "WHERE user_id = ?::uuid AND tenant_id = ?::uuid AND deleted_at IS NULL",
            Integer.class, existingId, palmeraie.toString());
        assertThat(count).isEqualTo(1);

        jdbc.update("DELETE FROM tenant_memberships WHERE user_id = ?::uuid AND tenant_id = ?::uuid",
            existingId, palmeraie.toString());
    }

    // ─── MATRICE DE SÉCURITÉ ──────────────────────────────────────────────────

    @Test
    void client_withoutCreateMemberships_403() {
        UUID palmeraie = tenantIdBySlug("palmeraie");
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/tenants/" + palmeraie + "/members"), HttpMethod.POST,
            jsonJwtEntity(body(uniqueEmail("x"), "A", "B"), clientBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void crossTenant_homuAdmin_intoPalmeraie_403_abac() {
        UUID palmeraie = tenantIdBySlug("palmeraie");
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/tenants/" + palmeraie + "/members"), HttpMethod.POST,
            jsonJwtEntity(body(uniqueEmail("y"), "A", "B"), tenantAdminBearer("homu")), String.class);
        // RESTAURATEUR homu détient CREATE:MEMBERSHIPS (passe @PreAuthorize) mais ABAC bloque
        // l'invitation hors de son tenant home.
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void unauthenticated_401() {
        UUID palmeraie = tenantIdBySlug("palmeraie");
        ResponseEntity<String> resp = restTemplate.postForEntity(
            url("/api/tenants/" + palmeraie + "/members"), Map.of("email", uniqueEmail("z")), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
