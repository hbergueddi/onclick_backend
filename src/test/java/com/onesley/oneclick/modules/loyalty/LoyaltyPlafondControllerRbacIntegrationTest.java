package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC + CRUD de la ressource {@code LOYALTY_CAP} (plafonds Lounge,
 * migration V44). Ressource admin-only (SUPERADMIN) → RESTAURATEUR/CLIENT = 403.
 */
class LoyaltyPlafondControllerRbacIntegrationTest extends AbstractIntegrationTest {

    private UUID createdId;

    @AfterEach
    void cleanup() {
        if (createdId != null) {
            jdbc.update("DELETE FROM loyalty_plafonds WHERE id = ?", createdId);
            createdId = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void superadmin_fullCrud_happyPath() {
        String admin = adminBearer();

        Map<String, Object> body = Map.of(
            "name", "Test-Cap-" + UUID.randomUUID(),
            "scope", "client",
            "value", 5000,
            "unit", "points",
            "enabled", true);
        ResponseEntity<Map> created = restTemplate.exchange(
            url("/api/loyalty/plafonds"), HttpMethod.POST, jsonJwtEntity(body, admin), Map.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody()).isNotNull();
        createdId = UUID.fromString((String) created.getBody().get("id"));
        assertThat(created.getBody().get("scope")).isEqualTo("client");

        // LIST (admin)
        ResponseEntity<Map[]> list = restTemplate.exchange(
            url("/api/loyalty/plafonds"), HttpMethod.GET, jwtEntity(admin), Map[].class);
        assertThat(list.getStatusCode().value()).isEqualTo(200);

        // PATCH enabled=false
        int patch = restTemplate.exchange(
            url("/api/loyalty/plafonds/" + createdId), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("enabled", false), admin), String.class).getStatusCode().value();
        assertThat(patch).isEqualTo(200);

        // DELETE (soft)
        int del = restTemplate.exchange(
            url("/api/loyalty/plafonds/" + createdId), HttpMethod.DELETE,
            jwtEntity(admin), String.class).getStatusCode().value();
        assertThat(del).isEqualTo(204);
    }

    @Test
    void restaurateur_list_returns403_adminOnly() {
        int status = restTemplate.exchange(
            url("/api/loyalty/plafonds"), HttpMethod.GET,
            jwtEntity(bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void client_create_returns403_adminOnly() {
        int status = restTemplate.exchange(
            url("/api/loyalty/plafonds"), HttpMethod.POST,
            jsonJwtEntity(Map.of("name", "x"), bearerForRole("CLIENT")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }
}
