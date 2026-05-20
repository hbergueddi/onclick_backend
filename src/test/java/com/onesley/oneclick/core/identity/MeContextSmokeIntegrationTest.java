package com.onesley.oneclick.core.identity;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke {@code GET /api/users/me/context} — réponse consolidée (profil + rôle +
 * menus + permissions) servie par le domaine identity.
 */
class MeContextSmokeIntegrationTest extends AbstractIntegrationTest {

    @Test
    void meContext_authenticated_returns200_withConsolidatedShape() {
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/users/me/context"), HttpMethod.GET, jwtEntity(adminBearer()), String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody())
            .contains("\"user\"")
            .contains("\"role\"")
            .contains("\"menus\"")
            .contains("\"permissions\"");
    }

    @Test
    void meContext_noJwt_returns401() {
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/users/me/context"), HttpMethod.GET, jwtEntity(null), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }
}
