package com.onesley.oneclick.core.identity;

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
 * P0 — Suppression de compte self-service ({@code DELETE /api/users/me}) sur la stack réelle.
 *
 * <p>Couvre : RBAC ({@code DELETE:PROFILE} accordé au rôle CLIENT par V103), 401 sans JWT,
 * 204 + soft-delete + anonymisation PII en base, accès révoqué après suppression, et
 * libération de l'email (ré-inscription possible — RGPD effacement). Conformité App Store
 * Review Guideline §5.1.1(v).
 */
class AccountDeletionFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void deleteMe_softDeletesAnonymizes_revokesAccess_andFreesEmail() throws Exception {
        String admin = adminBearer();
        String email = "del-it-" + UUID.randomUUID() + "@x.ma";
        String roleId = jdbc.queryForObject("SELECT id::text FROM roles WHERE code='CLIENT' LIMIT 1", String.class);

        // User CLIENT jetable.
        ResponseEntity<String> created = restTemplate.exchange(url("/api/users"), HttpMethod.POST, jsonJwtEntity(Map.of(
            "roleId", roleId, "email", email, "password", "password1234",
            "firstName", "Del", "lastName", "Test"), admin), String.class);
        assertThat(created.getStatusCode().is2xxSuccessful()).as("création user jetable").isTrue();
        String userId = om.readTree(created.getBody()).get("id").asText();
        String bearer = jwtIssuer.issueAccessToken(UUID.fromString(userId), "CLIENT").token();

        // RBAC : V103 a bien accordé DELETE:PROFILE au rôle CLIENT.
        Integer grant = jdbc.queryForObject(
            "SELECT count(*) FROM permissions p " +
            "JOIN roles r ON r.id = p.role_id JOIN menus m ON m.id = p.menu_id JOIN actions a ON a.id = p.action_id " +
            "WHERE r.code = 'CLIENT' AND m.code = 'PROFILE' AND a.code = 'DELETE'", Integer.class);
        assertThat(grant).as("grant DELETE:PROFILE au CLIENT (V103)").isGreaterThanOrEqualTo(1);

        // 401 sans JWT.
        assertThat(restTemplate.exchange(url("/api/users/me"), HttpMethod.DELETE, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // GET /me OK avant suppression.
        assertThat(restTemplate.exchange(url("/api/users/me"), HttpMethod.GET, jwtEntity(bearer), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // DELETE /me → 204.
        ResponseEntity<String> del = restTemplate.exchange(
            url("/api/users/me"), HttpMethod.DELETE, jwtEntity(bearer), String.class);
        assertThat(del.getStatusCode()).as("DELETE /me — body=%s", del.getBody()).isEqualTo(HttpStatus.NO_CONTENT);

        // DB : soft-delete + anonymisation PII.
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT deleted_at, email, status FROM users WHERE id = ?::uuid", userId);
        assertThat(row.get("deleted_at")).as("deleted_at posé").isNotNull();
        assertThat((String) row.get("email")).as("email anonymisé").startsWith("deleted-");
        assertThat((String) row.get("status")).isEqualTo("deleted");

        // Accès révoqué : même JWT, GET /me → 4xx (compte inaccessible).
        assertThat(restTemplate.exchange(url("/api/users/me"), HttpMethod.GET, jwtEntity(bearer), String.class)
            .getStatusCode().is4xxClientError()).as("accès révoqué après suppression").isTrue();

        // Email libéré : ré-inscription possible avec l'email d'origine (RGPD effacement).
        ResponseEntity<String> reReg = restTemplate.exchange(url("/api/users/register"), HttpMethod.POST,
            jsonJwtEntity(Map.of("email", email, "password", "password1234",
                "firstName", "Re", "lastName", "Reg"), null), String.class);
        assertThat(reReg.getStatusCode().is2xxSuccessful()).as("email libéré — body=%s", reReg.getBody()).isTrue();

        // self-clean : soft-delete (admin) le user re-créé.
        String reId = om.readTree(reReg.getBody()).get("id").asText();
        restTemplate.exchange(url("/api/users/" + reId), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }
}
