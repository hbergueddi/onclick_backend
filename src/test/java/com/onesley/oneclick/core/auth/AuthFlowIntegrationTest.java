package com.onesley.oneclick.core.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L4 « profondeur » — {@code /api/auth} chemin nominal : on crée un user (admin),
 * puis login → refresh → logout avec ses vrais tokens (BCrypt réel, JWT réel).
 */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String clientRoleId() { return jdbc.queryForObject("SELECT id::text FROM roles WHERE code='CLIENT' LIMIT 1", String.class); }

    @Test
    void login_refresh_logout_happyPath() throws Exception {
        String admin = adminBearer();
        String email = "l4-auth-" + UUID.randomUUID().toString().substring(0, 8) + "@x.ma";
        String password = "password1234";

        // 1. créer le user (admin)
        ResponseEntity<String> create = restTemplate.exchange(url("/api/users"), HttpMethod.POST,
            jsonJwtEntity(Map.of("roleId", clientRoleId(), "email", email, "password", password,
                "firstName", "L4", "lastName", "Auth"), admin), String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String userId = om.readTree(create.getBody()).get("id").asText();

        // 2. login → 200 + tokens
        ResponseEntity<String> login = restTemplate.exchange(url("/api/auth/login"), HttpMethod.POST,
            jsonJwtEntity(Map.of("email", email, "password", password), null), String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode tokens = om.readTree(login.getBody());
        assertThat(tokens.get("accessToken").asText()).isNotBlank();
        String refreshToken = tokens.get("refreshToken").asText();
        assertThat(refreshToken).isNotBlank();

        // 3. refresh → 200
        ResponseEntity<String> refresh = restTemplate.exchange(url("/api/auth/refresh"), HttpMethod.POST,
            jsonJwtEntity(Map.of("refreshToken", refreshToken), null), String.class);
        assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.OK);
        String newAccess = om.readTree(refresh.getBody()).get("accessToken").asText();

        // 4. logout (authentifié + body refreshToken) → 2xx
        assertThat(restTemplate.exchange(url("/api/auth/logout"), HttpMethod.POST,
            jsonJwtEntity(Map.of("refreshToken", refreshToken), newAccess), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();

        // 5. cleanup
        restTemplate.exchange(url("/api/users/" + userId), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void login_wrongPassword_4xx() {
        ResponseEntity<String> r = restTemplate.exchange(url("/api/auth/login"), HttpMethod.POST,
            jsonJwtEntity(Map.of("email", "inconnu-" + UUID.randomUUID() + "@x.ma", "password", "mauvais"), null), String.class);
        assertThat(r.getStatusCode().is4xxClientError()).isTrue();
    }
}
