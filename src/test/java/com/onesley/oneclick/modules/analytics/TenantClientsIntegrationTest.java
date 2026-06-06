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
 * RBAC/intégration L4 — CRM tenant-admin « Mes clients » (C4.1, ressource {@code TENANTS}, SUPERADMIN).
 *
 * <p>Stack réelle : SUPERADMIN → 200 + tableau JSON (agrégat natif exécuté en DB) ; rôle sans
 * {@code VIEW:TENANTS} → 403 ; sans JWT → 401 ; fiche d'un client inexistant → 404.</p>
 */
class TenantClientsIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private UUID palmeraieTenantId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = 'palmeraie'", String.class));
    }

    @Test
    void tenantClients_admin_returns200_array() throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/analytics/tenant-clients?tenantId=" + palmeraieTenantId()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(resp.getStatusCode())
            .as("reçu %s, body=%s", resp.getStatusCode(), resp.getBody())
            .isEqualTo(HttpStatus.OK);
        JsonNode body = om.readTree(resp.getBody());
        assertThat(body.isArray()).isTrue();
    }

    @Test
    void tenantClients_nonSuperAdmin_forbidden() {
        assertThat(restTemplate.exchange(
            url("/api/analytics/tenant-clients?tenantId=" + palmeraieTenantId()),
            HttpMethod.GET, jwtEntity(bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void tenantClients_noJwt_unauthorized() {
        assertThat(restTemplate.exchange(
            url("/api/analytics/tenant-clients?tenantId=" + palmeraieTenantId()),
            HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void tenantClientDetail_unknownClient_notFound() {
        assertThat(restTemplate.exchange(
            url("/api/analytics/tenant-clients/" + UUID.randomUUID() + "?tenantId=" + palmeraieTenantId()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
