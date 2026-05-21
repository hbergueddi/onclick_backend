package com.onesley.oneclick.security;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC (retour senior : « plus de hasRole, que hasAuthority »).
 * Matrice par rôle sur la stack de sécurité réelle :
 * <ul>
 *   <li>route admin ({@code VIEW:ROLES}) : sans JWT→401, CLIENT/RESTAURATEUR→403, SUPERADMIN→200 ;</li>
 *   <li>route d'écriture ({@code CREATE:RESTAURANTS}) : CLIENT→403 (payload valide, deny avant service) ;</li>
 *   <li>endpoints self {@code /me*} ({@code isAuthenticated}) : 200 pour tout authentifié, 401 sinon.</li>
 * </ul>
 */
class RbacAuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final String FAKE = "00000000-0000-0000-0000-000000000001";

    private int getStatus(String path, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.GET, jwtEntity(jwt), String.class)
            .getStatusCode().value();
    }

    // ─── Route admin : VIEW:ROLES ──────────────────────────────────────────────

    @Test
    void adminRoute_noJwt_returns401() {
        assertThat(getStatus("/api/roles", null)).isEqualTo(401);
    }

    @Test
    void adminRoute_client_returns403() {
        assertThat(getStatus("/api/roles", bearerForRole("CLIENT"))).isEqualTo(403);
    }

    @Test
    void adminRoute_restaurateur_returns403() {
        assertThat(getStatus("/api/roles", bearerForRole("RESTAURATEUR"))).isEqualTo(403);
    }

    @Test
    void adminRoute_superadmin_returns200() {
        assertThat(getStatus("/api/roles", adminBearer())).isEqualTo(200);
    }

    // ─── Route d'écriture : CREATE:RESTAURANTS (payload valide → deny avant service) ─

    @Test
    void writeRoute_client_returns403() {
        // payload VALIDE (tenantId/name/city) pour que la validation passe et que
        // ce soit bien l'autorisation qui refuse → 403, pas un 400.
        ResponseEntity<String> r = restTemplate.exchange(
            url("/api/restaurants"), HttpMethod.POST,
            jsonJwtEntity("{\"tenantId\":\"" + FAKE + "\",\"name\":\"X\",\"city\":\"Casa\"}", bearerForRole("CLIENT")),
            String.class);
        assertThat(r.getStatusCode().value()).isEqualTo(403);
    }

    // ─── Endpoints self : isAuthenticated() ────────────────────────────────────

    @Test
    void selfMe_noJwt_returns401() {
        assertThat(getStatus("/api/users/me", null)).isEqualTo(401);
    }

    @Test
    void selfMe_client_returns200() {
        assertThat(getStatus("/api/users/me", bearerForRole("CLIENT"))).isEqualTo(200);
    }

    @Test
    void selfMe_restaurateur_returns200() {
        assertThat(getStatus("/api/users/me", bearerForRole("RESTAURATEUR"))).isEqualTo(200);
    }

    @Test
    void selfMePermissions_client_returns200() {
        assertThat(getStatus("/api/users/me/permissions", bearerForRole("CLIENT"))).isEqualTo(200);
    }

    @Test
    void selfMeContext_client_returns200() {
        assertThat(getStatus("/api/users/me/context", bearerForRole("CLIENT"))).isEqualTo(200);
    }
}
