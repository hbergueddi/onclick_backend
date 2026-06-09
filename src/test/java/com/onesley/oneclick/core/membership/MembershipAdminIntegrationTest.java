package com.onesley.oneclick.core.membership;

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
 * Intégration P3 — liste des membres {@code GET /api/tenants/{tenantId}/members} (RBAC VIEW:MEMBERSHIPS
 * + ABAC own-tenant) ET vérification du flip identité V95 (clients de programme → home oneclick).
 */
class MembershipAdminIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private UUID tenantIdBySlug(String slug) {
        return UUID.fromString(jdbc.queryForObject("SELECT id::text FROM tenants WHERE slug = ?", String.class, slug));
    }

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

    // ─── Liste membres ────────────────────────────────────────────────────────

    @Test
    void palmeraieAdmin_listsMembers_enriched_200() throws Exception {
        UUID palmeraie = tenantIdBySlug("palmeraie");
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/tenants/" + palmeraie + "/members"), HttpMethod.GET,
            jwtEntity(tenantAdminBearer("palmeraie")), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode arr = om.readTree(resp.getBody());
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).as("palmeraie a des membres (backfill V90)").isGreaterThanOrEqualTo(1);
        assertThat(arr.get(0).has("email")).isTrue();
        assertThat(arr.get(0).get("status").asText()).isEqualTo("active");
    }

    @Test
    void palmeraieAdmin_getsMemberKpis_200() throws Exception {
        UUID palmeraie = tenantIdBySlug("palmeraie");
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/tenants/" + palmeraie + "/members/kpis"), HttpMethod.GET,
            jwtEntity(tenantAdminBearer("palmeraie")), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(resp.getBody()).get("totalActive").asLong()).isGreaterThanOrEqualTo(1L);
    }

    @Test
    void superAdmin_listsAnyTenant_200() {
        UUID palmeraie = tenantIdBySlug("palmeraie");
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/tenants/" + palmeraie + "/members"), HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void client_withoutViewMemberships_403() {
        UUID palmeraie = tenantIdBySlug("palmeraie");
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/tenants/" + palmeraie + "/members"), HttpMethod.GET, jwtEntity(clientBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void crossTenant_homuAdmin_listsPalmeraie_403_abac() {
        UUID palmeraie = tenantIdBySlug("palmeraie");
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/tenants/" + palmeraie + "/members"), HttpMethod.GET,
            jwtEntity(tenantAdminBearer("homu")), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── Flip identité V95 ──────────────────────────────────────────────────────

    @Test
    void v95_flip_noProgramClientLeftOutsideOneclickHome() {
        Integer leftover = jdbc.queryForObject(
            "SELECT count(*) FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL AND u.tenant_id IS NOT NULL "
            + "AND u.tenant_id <> (SELECT id FROM tenants WHERE slug = 'oneclick') "
            + "AND EXISTS (SELECT 1 FROM tenant_memberships tm "
            + "            WHERE tm.user_id = u.id AND tm.status = 'active' AND tm.deleted_at IS NULL)",
            Integer.class);
        assertThat(leftover).as("aucun client de programme hors home oneclick après V95").isZero();
    }

    @Test
    void v95_flip_palmeraieMembersNowHomeOneclick() {
        Integer flipped = jdbc.queryForObject(
            "SELECT count(*) FROM users u JOIN tenants t ON t.id = u.tenant_id "
            + "WHERE t.slug = 'oneclick' AND EXISTS ("
            + "  SELECT 1 FROM tenant_memberships tm JOIN tenants pt ON pt.id = tm.tenant_id "
            + "  WHERE tm.user_id = u.id AND pt.slug = 'palmeraie' "
            + "    AND tm.status = 'active' AND tm.deleted_at IS NULL)",
            Integer.class);
        assertThat(flipped).as("les membres palmeraie ont le home oneclick + gardent leur membership")
            .isGreaterThan(0);
    }
}
