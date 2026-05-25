package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration P3 (migration V36) — retrait des sur-droits CLIENT.
 *
 * <p>Après V36, le rôle CLIENT n'a plus {@code VIEW} sur STAFF / TABLES / ZONES /
 * SERVICES (ni {@code DELETE:SUPPORT}). Ces lectures de config restaurant sont
 * refusées au gate {@code @PreAuthorize} (403). L'admin garde l'accès.
 *
 * <p>Fixture CLIENT JETABLE (id neuf) : indispensable car le {@code userDetails}
 * est mis en cache (Redis, TTL 1 h) — un CLIENT déjà chargé garderait ses anciennes
 * autorités jusqu'à éviction. Un id neuf force un chargement FRAIS post-V36.
 * (NB prod : après déploiement de V36, flusher le cache userDetails ou attendre le TTL.)
 */
class ClientOvergrantRevocationIntegrationTest extends AbstractIntegrationTest {

    private UUID clientUserId;
    private String clientBearer;

    @BeforeEach
    void createFreshClient() {
        clientUserId = UUID.fromString(jdbc.queryForObject(
            "INSERT INTO users (role_id, email, password_hash, first_name, last_name) "
            + "VALUES ((SELECT id FROM roles WHERE code = 'CLIENT'), ?, 'x', 'Rbac', 'Client') "
            + "RETURNING id::text",
            String.class, "rbac-client-" + UUID.randomUUID() + "@test.local"));
        clientBearer = jwtIssuer.issueAccessToken(clientUserId, "CLIENT").token();
    }

    @AfterEach
    void dropFreshClient() {
        if (clientUserId != null) jdbc.update("DELETE FROM users WHERE id = ?", clientUserId);
    }

    private String restaurantId() {
        return jdbc.queryForObject(
            "SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class);
    }

    private int get(String path, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.GET, jwtEntity(jwt), String.class)
            .getStatusCode().value();
    }

    @Test
    void client_cannotViewServices_returns403() {
        assertThat(get("/api/restaurants/" + restaurantId() + "/services", clientBearer)).isEqualTo(403);
    }

    @Test
    void client_cannotViewTables_returns403() {
        assertThat(get("/api/restaurants/" + restaurantId() + "/tables", clientBearer)).isEqualTo(403);
    }

    @Test
    void client_cannotViewZones_returns403() {
        assertThat(get("/api/restaurants/" + restaurantId() + "/zones", clientBearer)).isEqualTo(403);
    }

    @Test
    void client_cannotViewStaff_returns403() {
        assertThat(get("/api/restaurants/" + restaurantId() + "/staff", clientBearer)).isEqualTo(403);
    }

    @Test
    void admin_canViewServices_returns200() {
        assertThat(get("/api/restaurants/" + restaurantId() + "/services", adminBearer())).isEqualTo(200);
    }
}
