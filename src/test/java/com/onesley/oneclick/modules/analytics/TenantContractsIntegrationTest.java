package com.onesley.oneclick.modules.analytics;

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
 * RBAC/intégration L4 — portail tenant-admin « Contrats » (C4.6, ressource {@code TENANTS}).
 *
 * <p>Stack réelle : SUPERADMIN → 200 + objet {contracts, summary} (agrégat natif JOIN
 * contracts→restaurants) ; rôle sans {@code VIEW:TENANTS} → 403 ; sans JWT → 401.</p>
 */
class TenantContractsIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private UUID palmeraieTenantId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = 'palmeraie'", String.class));
    }

    @Test
    void tenantContracts_admin_returns200_withShape() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/analytics/tenant-contracts?tenantId=" + palmeraieTenantId()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode())
            .as("reçu %s, body=%s", resp.getStatusCode(), resp.getBody())
            .isEqualTo(HttpStatus.OK);
        JsonNode body = om.readTree(resp.getBody());
        assertThat(body.get("contracts").isArray()).isTrue();
        assertThat(body.get("summary").has("active")).isTrue();
        assertThat(body.get("summary").has("byStatus")).isTrue();
    }

    @Test
    void tenantContracts_nonSuperAdmin_forbidden() {
        assertThat(restTemplate.exchange(
            url("/api/analytics/tenant-contracts?tenantId=" + palmeraieTenantId()),
            HttpMethod.GET, jwtEntity(bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void tenantContracts_noJwt_unauthorized() {
        assertThat(restTemplate.exchange(
            url("/api/analytics/tenant-contracts?tenantId=" + palmeraieTenantId()),
            HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
