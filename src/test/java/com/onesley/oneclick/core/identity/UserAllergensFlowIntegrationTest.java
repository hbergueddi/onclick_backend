package com.onesley.oneclick.core.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V65 — {@code allergens} sur le profil (PersonalInfo), parité legacy
 * {@code profiles.allergens}. Vérifie sur la stack réelle (HTTP + JWT HS256 +
 * filter chain → controller → service → repo → {@code oneclick_enterprise}) que
 * {@code PATCH /api/users/me {allergens}} persiste et que {@code GET /api/users/me}
 * (UserDto) + {@code /me/context} renvoient la liste. Self par construction
 * (JWT.sub) — pas de nouvelle autorité (réutilise {@code UPDATE:PROFILE}).
 *
 * <p>Calque {@link UserCityFlowIntegrationTest} (même item self-service via PATCH /me).
 */
class UserAllergensFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    /** User CLIENT jetable (évite de muter les données seed). */
    private String createClient(String admin) throws Exception {
        String roleId = jdbc.queryForObject("SELECT id::text FROM roles WHERE code='CLIENT' LIMIT 1", String.class);
        var r = restTemplate.exchange(url("/api/users"), HttpMethod.POST, jsonJwtEntity(Map.of(
            "roleId", roleId, "email", "allergens-it-" + UUID.randomUUID() + "@x.ma",
            "password", "password1234", "firstName", "Allergens", "lastName", "Test"), admin), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("création user jetable").isTrue();
        return om.readTree(r.getBody()).get("id").asText();
    }

    @Test
    void patchMe_allergens_persists_andReturnedByGetMe() throws Exception {
        String admin = adminBearer();
        String userId = createClient(admin);
        String bearer = jwtIssuer.issueAccessToken(UUID.fromString(userId), "CLIENT").token();

        // GET /me : allergens vide au départ (colonne NOT NULL DEFAULT '{}').
        ResponseEntity<String> before = restTemplate.exchange(
            url("/api/users/me"), HttpMethod.GET, jwtEntity(bearer), String.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(before.getBody()).get("allergens").isArray()).isTrue();
        assertThat(om.readTree(before.getBody()).get("allergens")).isEmpty();

        // PATCH /me {allergens} → 200 + liste renvoyée dans le UserDto de réponse.
        // HashMap (pas Map.of) : on patche AUSSI un autre champ pour prouver l'indépendance.
        Map<String, Object> patch = new HashMap<>();
        patch.put("allergens", List.of("gluten", "lactose", "peanuts"));
        patch.put("city", "Marrakech");
        ResponseEntity<String> patched = restTemplate.exchange(
            url("/api/users/me"), HttpMethod.PATCH, jsonJwtEntity(patch, bearer), String.class);
        assertThat(patched.getStatusCode())
            .as("PATCH /me allergens — reçu %s, body=%s", patched.getStatusCode(), patched.getBody())
            .isEqualTo(HttpStatus.OK);
        var patchedArr = om.readTree(patched.getBody()).get("allergens");
        assertThat(patchedArr).hasSize(3);
        assertThat(List.of(patchedArr.get(0).asText(), patchedArr.get(1).asText(), patchedArr.get(2).asText()))
            .containsExactly("gluten", "lactose", "peanuts");

        // GET /me re-lit la liste persistée (round-trip DB → text[] → List<String>).
        ResponseEntity<String> after = restTemplate.exchange(
            url("/api/users/me"), HttpMethod.GET, jwtEntity(bearer), String.class);
        var afterArr = om.readTree(after.getBody()).get("allergens");
        assertThat(afterArr).hasSize(3);
        assertThat(afterArr.get(0).asText()).isEqualTo("gluten");

        // /me/context (bootstrap front consolidé) expose aussi les allergènes.
        ResponseEntity<String> ctx = restTemplate.exchange(
            url("/api/users/me/context"), HttpMethod.GET, jwtEntity(bearer), String.class);
        assertThat(ctx.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(ctx.getBody()).get("user").get("allergens")).hasSize(3);

        // Re-PATCH : remplace la liste (pas d'accumulation) — un seul allergène.
        Map<String, Object> patch2 = new HashMap<>();
        patch2.put("allergens", List.of("fish"));
        restTemplate.exchange(url("/api/users/me"), HttpMethod.PATCH, jsonJwtEntity(patch2, bearer), String.class);
        ResponseEntity<String> after2 = restTemplate.exchange(
            url("/api/users/me"), HttpMethod.GET, jwtEntity(bearer), String.class);
        var after2Arr = om.readTree(after2.getBody()).get("allergens");
        assertThat(after2Arr).hasSize(1);
        assertThat(after2Arr.get(0).asText()).isEqualTo("fish");

        // 401 : pas de JWT.
        assertThat(restTemplate.exchange(url("/api/users/me"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("allergens", List.of("soy")), null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // self-clean.
        restTemplate.exchange(url("/api/users/" + userId), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }
}
