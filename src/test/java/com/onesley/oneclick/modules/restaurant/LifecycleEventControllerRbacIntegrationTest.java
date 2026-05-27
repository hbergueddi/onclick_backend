package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC + lecture/écriture de la ressource {@code LIFECYCLE}
 * (journal cycle de vie, migration V45). Append-only, admin-only (SUPERADMIN) :
 * RESTAURATEUR/CLIENT = 403.
 */
class LifecycleEventControllerRbacIntegrationTest extends AbstractIntegrationTest {

    private UUID createdId;

    @AfterEach
    void cleanup() {
        if (createdId != null) {
            jdbc.update("DELETE FROM lifecycle_events WHERE id = ?", createdId);
            createdId = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void superadmin_createThenList() {
        String admin = adminBearer();

        ResponseEntity<Map> created = restTemplate.exchange(
            url("/api/lifecycle-events"), HttpMethod.POST,
            jsonJwtEntity(Map.of("event", "validation", "actor", "Test Admin", "details", "smoke"), admin),
            Map.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody()).isNotNull();
        createdId = UUID.fromString((String) created.getBody().get("id"));
        assertThat(created.getBody().get("event")).isEqualTo("validation");

        ResponseEntity<Map[]> list = restTemplate.exchange(
            url("/api/lifecycle-events"), HttpMethod.GET, jwtEntity(admin), Map[].class);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void restaurateur_list_returns403_adminOnly() {
        int status = restTemplate.exchange(
            url("/api/lifecycle-events"), HttpMethod.GET,
            jwtEntity(bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void client_create_returns403_adminOnly() {
        int status = restTemplate.exchange(
            url("/api/lifecycle-events"), HttpMethod.POST,
            jsonJwtEntity(Map.of("event", "x"), bearerForRole("CLIENT")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }
}
