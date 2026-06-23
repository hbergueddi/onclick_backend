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
 * P1 — Signup : consentement CGU persisté (RGPD, V104 {@code cgu_accepted_at}) + anti-énumération
 * (message générique sans echo de l'email). Sur la stack réelle (POST /api/users/register public).
 */
class UserRegistrationConsentIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private ResponseEntity<String> register(String email, Boolean cguAccepted) {
        StringBuilder b = new StringBuilder("{")
            .append("\"email\":\"").append(email).append("\",")
            .append("\"password\":\"password1234\",\"firstName\":\"Consent\",\"lastName\":\"Test\"");
        if (cguAccepted != null) b.append(",\"cguAccepted\":").append(cguAccepted);
        b.append("}");
        return restTemplate.exchange(url("/api/users/register"), HttpMethod.POST,
            jsonJwtEntity(b.toString(), null), String.class);
    }

    @Test
    void register_withCguAccepted_persistsCguAcceptedAt() throws Exception {
        String admin = adminBearer();
        String email = "consent-" + UUID.randomUUID() + "@x.ma";

        ResponseEntity<String> r = register(email, true);
        assertThat(r.getStatusCode()).as("register CGU true — body=%s", r.getBody()).isEqualTo(HttpStatus.CREATED);
        String id = om.readTree(r.getBody()).get("id").asText();

        Map<String, Object> row = jdbc.queryForMap("SELECT cgu_accepted_at FROM users WHERE id = ?::uuid", id);
        assertThat(row.get("cgu_accepted_at")).as("trace consentement CGU persistée").isNotNull();

        restTemplate.exchange(url("/api/users/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void register_withoutCgu_leavesNull_backwardCompatible() throws Exception {
        String admin = adminBearer();
        String email = "nocgu-" + UUID.randomUUID() + "@x.ma";

        ResponseEntity<String> r = register(email, null);  // client pas encore à jour → champ absent
        assertThat(r.getStatusCode()).as("register sans CGU reste accepté (compat) — body=%s", r.getBody())
            .isEqualTo(HttpStatus.CREATED);
        String id = om.readTree(r.getBody()).get("id").asText();

        Map<String, Object> row = jdbc.queryForMap("SELECT cgu_accepted_at FROM users WHERE id = ?::uuid", id);
        assertThat(row.get("cgu_accepted_at")).isNull();

        restTemplate.exchange(url("/api/users/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void register_duplicateEmail_returns409_withoutEchoingEmail() throws Exception {
        String admin = adminBearer();
        String email = "dup-" + UUID.randomUUID() + "@x.ma";

        ResponseEntity<String> first = register(email, true);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = om.readTree(first.getBody()).get("id").asText();

        ResponseEntity<String> dup = register(email, true);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        // Anti-énumération : le corps d'erreur ne doit pas contenir l'email testé.
        assertThat(dup.getBody()).as("message générique sans echo email").doesNotContain(email);

        restTemplate.exchange(url("/api/users/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }
}
