package com.onesley.oneclick.core.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/users} : lifecycle + password + lookups + /me*. */
class UserFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String roleId() { return jdbc.queryForObject("SELECT id::text FROM roles WHERE code='CLIENT' LIMIT 1", String.class); }
    private String anEmail() { return jdbc.queryForObject("SELECT email FROM users WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void user_fullLifecycle_create_patch_password_delete() throws Exception {
        String admin = adminBearer();
        String email = "l4-" + UUID.randomUUID().toString().substring(0, 8) + "@x.ma";

        ResponseEntity<String> post = restTemplate.exchange(url("/api/users"), HttpMethod.POST,
            jsonJwtEntity(Map.of("roleId", roleId(), "email", email, "password", "password1", "firstName", "L4", "lastName", "User"), admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = om.readTree(post.getBody()).get("id").asText();

        assertThat(restTemplate.exchange(url("/api/users/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> patch = restTemplate.exchange(url("/api/users/" + id), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("firstName", "L4 Patched"), admin), String.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(patch.getBody()).get("firstName").asText()).isEqualTo("L4 Patched");

        // changePassword est owner-exact : un admin sur le compte d'autrui → 403.
        assertThat(restTemplate.exchange(url("/api/users/" + id + "/password"), HttpMethod.POST,
            jsonJwtEntity(Map.of("currentPassword", "password1", "newPassword", "newpass1234"), admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(restTemplate.exchange(url("/api/users/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.exchange(url("/api/users/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void patchMe_asClient_updatesOwnProfile_withoutAdminAuthority() throws Exception {
        String admin = adminBearer();
        String email = "l4-me-" + UUID.randomUUID().toString().substring(0, 8) + "@x.ma";
        ResponseEntity<String> post = restTemplate.exchange(url("/api/users"), HttpMethod.POST,
            jsonJwtEntity(Map.of("roleId", roleId(), "email", email, "password", "password1", "firstName", "Self", "lastName", "Svc"), admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = om.readTree(post.getBody()).get("id").asText();

        // sans JWT → 401
        assertThat(restTemplate.exchange(url("/api/users/me"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("firstName", "X"), null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // un CLIENT (qui n'a PAS UPDATE:USERS) édite SON propre profil via /me → 200
        // (firstName seul : phone a une contrainte d'unicité → éviter les collisions seed)
        String bearerClient = jwtIssuer.issueAccessToken(UUID.fromString(id), "CLIENT").token();
        ResponseEntity<String> patch = restTemplate.exchange(url("/api/users/me"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("firstName", "SelfEdited"), bearerClient), String.class);
        assertThat(patch.getStatusCode()).as("CLIENT self-edit via /me — body=%s", patch.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(patch.getBody()).get("firstName").asText()).isEqualTo("SelfEdited");

        // self-clean
        restTemplate.exchange(url("/api/users/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void changeMyPassword_asClient_self_verifiesCurrent() throws Exception {
        String admin = adminBearer();
        String email = "l4-pw-" + UUID.randomUUID().toString().substring(0, 8) + "@x.ma";
        ResponseEntity<String> post = restTemplate.exchange(url("/api/users"), HttpMethod.POST,
            jsonJwtEntity(Map.of("roleId", roleId(), "email", email, "password", "password1", "firstName", "Pw", "lastName", "Self"), admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = om.readTree(post.getBody()).get("id").asText();
        String bearer = jwtIssuer.issueAccessToken(UUID.fromString(id), "CLIENT").token();

        // sans JWT → 401
        assertThat(restTemplate.exchange(url("/api/users/me/password"), HttpMethod.POST,
            jsonJwtEntity(Map.of("currentPassword", "password1", "newPassword", "newpass1234"), null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // mauvais mot de passe courant → 4xx (refusé)
        assertThat(restTemplate.exchange(url("/api/users/me/password"), HttpMethod.POST,
            jsonJwtEntity(Map.of("currentPassword", "WRONGpwd", "newPassword", "newpass1234"), bearer), String.class)
            .getStatusCode().is2xxSuccessful()).isFalse();

        // CLIENT change SON mot de passe (currentPassword correct, sans UPDATE:USERS) → 204
        assertThat(restTemplate.exchange(url("/api/users/me/password"), HttpMethod.POST,
            jsonJwtEntity(Map.of("currentPassword", "password1", "newPassword", "newpass1234"), bearer), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // self-clean
        restTemplate.exchange(url("/api/users/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void user_readsAndMe() {
        String admin = adminBearer();
        for (String path : List.of("/api/users?page=0&size=5", "/api/users/me", "/api/users/me/context",
            "/api/users/me/permissions", "/api/users/roles-distribution", "/api/users/by-role?role=CLIENT&page=0&size=5",
            "/api/users/by-email?email=" + anEmail())) {
            assertThat(restTemplate.exchange(url(path), HttpMethod.GET, jwtEntity(admin), String.class)
                .getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
        }
        assertThat(restTemplate.exchange(url("/api/users/by-ids"), HttpMethod.POST,
            jsonJwtEntity(List.of(SEED_SUPERADMIN_ID.toString()), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/users/search"), HttpMethod.POST,
            jsonJwtEntity(Map.of("criteria", List.of(), "page", 0, "size", 5), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void user_lookups_unknown_404() {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/users/" + UUID.randomUUID()), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(url("/api/users/by-phone?phone=0600000000"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(restTemplate.exchange(url("/api/users/by-referral-code?code=OC-INCONNU"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_invalidBody_400() {
        assertThat(restTemplate.exchange(url("/api/users"), HttpMethod.POST,
            jsonJwtEntity(Map.of("firstName", "sans email ni role"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void list_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/users?page=0&size=5"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
