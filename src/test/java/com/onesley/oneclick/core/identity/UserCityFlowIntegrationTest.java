package com.onesley.oneclick.core.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ITEM 1 — {@code city} sur le profil (PersonalInfo). Vérifie sur la stack réelle que
 * {@code PATCH /api/users/me {city}} persiste et que {@code GET /api/users/me} (UserDto)
 * renvoie la ville. Self par construction (JWT.sub) — pas de nouvelle autorité (réutilise
 * {@code UPDATE:PROFILE} / {@code VIEW:PROFILE}).
 */
class UserCityFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    /** User CLIENT jetable (évite de muter les données seed). */
    private String createClient(String admin) throws Exception {
        String roleId = jdbc.queryForObject("SELECT id::text FROM roles WHERE code='CLIENT' LIMIT 1", String.class);
        var r = restTemplate.exchange(url("/api/users"), HttpMethod.POST, jsonJwtEntity(Map.of(
            "roleId", roleId, "email", "city-it-" + UUID.randomUUID() + "@x.ma",
            "password", "password1234", "firstName", "City", "lastName", "Test"), admin), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("création user jetable").isTrue();
        return om.readTree(r.getBody()).get("id").asText();
    }

    @Test
    void patchMe_city_persists_andReturnedByGetMe() throws Exception {
        String admin = adminBearer();
        String userId = createClient(admin);
        String bearer = jwtIssuer.issueAccessToken(UUID.fromString(userId), "CLIENT").token();

        // GET /me : city null au départ.
        ResponseEntity<String> before = restTemplate.exchange(
            url("/api/users/me"), HttpMethod.GET, jwtEntity(bearer), String.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(before.getBody()).get("city").isNull()).isTrue();

        // PATCH /me {city} → 200 + city renvoyée dans le UserDto de réponse.
        // HashMap (pas Map.of) : on patche AUSSI un autre champ pour prouver l'indépendance.
        Map<String, Object> patch = new HashMap<>();
        patch.put("city", "Marrakech");
        ResponseEntity<String> patched = restTemplate.exchange(
            url("/api/users/me"), HttpMethod.PATCH, jsonJwtEntity(patch, bearer), String.class);
        assertThat(patched.getStatusCode())
            .as("PATCH /me city — reçu %s, body=%s", patched.getStatusCode(), patched.getBody())
            .isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(patched.getBody()).get("city").asText()).isEqualTo("Marrakech");

        // GET /me re-lit la ville persistée (round-trip DB).
        ResponseEntity<String> after = restTemplate.exchange(
            url("/api/users/me"), HttpMethod.GET, jwtEntity(bearer), String.class);
        assertThat(om.readTree(after.getBody()).get("city").asText()).isEqualTo("Marrakech");

        // /me/context (bootstrap front consolidé) expose aussi la ville.
        ResponseEntity<String> ctx = restTemplate.exchange(
            url("/api/users/me/context"), HttpMethod.GET, jwtEntity(bearer), String.class);
        assertThat(ctx.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(ctx.getBody()).get("user").get("city").asText()).isEqualTo("Marrakech");

        // 401 : pas de JWT.
        assertThat(restTemplate.exchange(url("/api/users/me"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("city", "Rabat"), null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // self-clean.
        restTemplate.exchange(url("/api/users/" + userId), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }
}
