package com.onesley.oneclick.modules.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC/intégration L4 — CrossTenantDashboard (C3, ressource {@code TENANTS}, SUPERADMIN-only).
 *
 * <p>Vérifie sur la stack réelle : SUPERADMIN → 200 avec {@code rows} + {@code summary} (agrégat
 * natif cross-tenant exécuté en DB), un rôle sans {@code VIEW:TENANTS} → 403, sans JWT → 401.</p>
 */
class CrossTenantStatsIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void crossTenantStats_admin_returns200_withRowsAndSummary() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/analytics/cross-tenant-stats?days=30"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode())
            .as("reçu %s, body=%s", resp.getStatusCode(), resp.getBody())
            .isEqualTo(HttpStatus.OK);
        JsonNode body = om.readTree(resp.getBody());
        assertThat(body.get("rows").isArray()).isTrue();
        assertThat(body.has("summary")).isTrue();
        assertThat(body.get("summary").get("totalTenants").asInt()).isGreaterThanOrEqualTo(1);
        // Le tenant PCC seedé (slug palmeraie) figure dans les lignes cross-tenant.
        assertThat(resp.getBody()).contains("palmeraie");
    }

    @Test
    void crossTenantStats_nonSuperAdmin_forbidden() {
        assertThat(restTemplate.exchange(url("/api/analytics/cross-tenant-stats"),
            HttpMethod.GET, jwtEntity(bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void crossTenantStats_noJwt_unauthorized() {
        assertThat(restTemplate.exchange(url("/api/analytics/cross-tenant-stats"),
            HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
