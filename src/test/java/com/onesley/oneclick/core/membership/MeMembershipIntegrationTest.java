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
 * Intégration P3 — {@code GET /api/me/memberships} (révélation self-service des espaces programme).
 */
class MeMembershipIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void member_getsOwnMemberships_withTenantSlug() throws Exception {
        String memberId = jdbc.queryForObject(
            "SELECT tm.user_id::text FROM tenant_memberships tm JOIN tenants t ON t.id = tm.tenant_id "
            + "WHERE t.slug = 'palmeraie' AND tm.status = 'active' AND tm.deleted_at IS NULL "
            + "ORDER BY tm.user_id LIMIT 1", String.class);
        String bearer = jwtIssuer.issueAccessToken(UUID.fromString(memberId), "CLIENT").token();

        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/me/memberships"), HttpMethod.GET, jwtEntity(bearer), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode arr = om.readTree(resp.getBody());
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isGreaterThanOrEqualTo(1);
        boolean hasPalmeraie = false;
        for (JsonNode n : arr) {
            if ("palmeraie".equals(n.get("tenantSlug").asText())) {
                hasPalmeraie = true;
                assertThat(n.get("tenantName").asText()).isNotBlank();
                assertThat(n.get("status").asText()).isEqualTo("active");
            }
        }
        assertThat(hasPalmeraie).as("le membre palmeraie voit sa membership révélée").isTrue();
    }

    @Test
    void nonMember_getsEmptyArray() throws Exception {
        String nonMemberId = jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL "
            + "AND NOT EXISTS (SELECT 1 FROM tenant_memberships tm WHERE tm.user_id = u.id AND tm.deleted_at IS NULL) "
            + "ORDER BY u.id LIMIT 1", String.class);
        String bearer = jwtIssuer.issueAccessToken(UUID.fromString(nonMemberId), "CLIENT").token();

        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/me/memberships"), HttpMethod.GET, jwtEntity(bearer), String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(resp.getBody()).size()).isZero();
    }

    @Test
    void unauthenticated_401() {
        ResponseEntity<String> resp = restTemplate.getForEntity(url("/api/me/memberships"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
