package com.onesley.oneclick.core.auth.internal;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests d'intégration de l'activation de compte membre (Gap #10) — flux 1er login.
 *
 * <p>Couvre E2E le contrat senior : token invalide → 400 ; lien valide → set-password +
 * session JWT ; preuve par <b>login réussi</b> avec le nouveau mot de passe ; single-use
 * (2ᵉ acceptation rejetée). Endpoint PUBLIC (gardé par le token) — pas de JWT requis.</p>
 */
class AccountActivationInviteIntegrationTest extends AbstractIntegrationTest {

    /** Crée un membre (mdp inutilisable, comme à l'enrôlement) + une invitation pending. */
    private String seedUserWithInvite(String rawToken) {
        String email = "activate-" + UUID.randomUUID() + "@test.local";
        String userId = jdbc.queryForObject(
            "INSERT INTO users (role_id, email, password_hash, first_name, last_name) "
            + "VALUES ((SELECT id FROM roles WHERE code = 'CLIENT'), ?, 'unusable-hash', 'New', 'Member') "
            + "RETURNING id::text",
            String.class, email);
        jdbc.update(
            "INSERT INTO account_activation_invites (id, user_id, email, token_hash, status, expires_at) "
            + "VALUES (?, ?::uuid, ?, ?, 'pending', now() + interval '1 day')",
            UUID.randomUUID(), userId, email, AccountActivationService.sha256Hex(rawToken));
        return email;
    }

    @Test
    void acceptActivation_invalidToken_returns400() {
        ResponseEntity<String> res = restTemplate.postForEntity(
            url("/api/auth/accept-activation-invite"),
            Map.of("token", "totally-bogus-token", "password", "NewSecret1"),
            String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void acceptActivation_validToken_setsPassword_thenLoginSucceeds() {
        String raw = "raw-" + UUID.randomUUID();
        String email = seedUserWithInvite(raw);

        // 1. Accept → 200 + session (access token).
        ResponseEntity<String> accept = restTemplate.postForEntity(
            url("/api/auth/accept-activation-invite"),
            Map.of("token", raw, "password", "NewSecret1"),
            String.class);
        assertThat(accept.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accept.getBody()).contains("accessToken");

        // 2. Preuve que le mot de passe a bien été défini : login réussit.
        ResponseEntity<String> login = restTemplate.postForEntity(
            url("/api/auth/login"),
            Map.of("email", email, "password", "NewSecret1"),
            String.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).contains("accessToken");

        // 3. Single-use : 2ᵉ acceptation avec le même token → 400.
        ResponseEntity<String> reuse = restTemplate.postForEntity(
            url("/api/auth/accept-activation-invite"),
            Map.of("token", raw, "password", "AnotherPass2"),
            String.class);
        assertThat(reuse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void acceptActivation_blankPassword_returns400_validation() {
        String raw = "raw-" + UUID.randomUUID();
        seedUserWithInvite(raw);
        ResponseEntity<String> res = restTemplate.postForEntity(
            url("/api/auth/accept-activation-invite"),
            Map.of("token", raw, "password", ""), // @NotBlank + @Size(min=8) → 400
            String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
