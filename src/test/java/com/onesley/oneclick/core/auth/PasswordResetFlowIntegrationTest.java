package com.onesley.oneclick.core.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase A enrollment — flux complet « mot de passe oublié » par code OTP email (reconnexion forcée),
 * sur la stack réelle (profil par défaut : {@code email-verification-required=false} → compte actif au signup).
 *
 * <ol>
 *   <li>register public → compte actif</li>
 *   <li>forgot-password → 202 GÉNÉRIQUE (compte existant) + OTP {@code reset_password} persisté</li>
 *   <li>reset-password (code + nouveau mot de passe) → 204, aucune session renvoyée (reconnexion forcée)</li>
 *   <li>login nouveau mot de passe → 200 ; ancien mot de passe → 400</li>
 *   <li>réutilisation du code → 400 (usage unique)</li>
 *   <li>forgot-password sur email inconnu → 202 identique (anti-énumération)</li>
 *   <li>validations (email vide, mot de passe < 10) → 400 ; endpoints PUBLICS (pas 401)</li>
 * </ol>
 */
class PasswordResetFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private static final String OLD_PWD = "password1234";

    private ResponseEntity<String> register(String email) {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + OLD_PWD + "\","
            + "\"firstName\":\"Reset\",\"lastName\":\"Flow\",\"cguAccepted\":true}";
        return restTemplate.exchange(url("/api/users/register"), HttpMethod.POST,
            jsonJwtEntity(body, null), String.class);
    }

    private int post(String path, String body) {
        return restTemplate.exchange(url(path), HttpMethod.POST, jsonJwtEntity(body, null), String.class)
            .getStatusCode().value();
    }

    private int login(String email, String password) {
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        return post("/api/auth/login", body);
    }

    private String latestResetCode(String userId) {
        return jdbc.queryForObject(
            "SELECT code FROM otp_requests WHERE user_id = ?::uuid AND purpose = 'reset_password' "
                + "ORDER BY created_at DESC LIMIT 1", String.class, userId);
    }

    @Test
    void forgotThenReset_endToEnd_forcesRelogin_singleUse() throws Exception {
        String admin = adminBearer();
        String email = "reset-" + UUID.randomUUID() + "@x.ma";
        String userId = om.readTree(register(email).getBody()).get("id").asText();

        // 1) Demande de réinitialisation → 202 générique.
        assertThat(post("/api/auth/forgot-password", "{\"email\":\"" + email + "\"}"))
            .as("forgot-password compte existant").isEqualTo(202);

        // 2) Le code reset_password est persisté (l'email part via OtpEmailListener, stub-safe en test).
        String code = latestResetCode(userId);
        assertThat(code).matches("\\d{6}");

        // 3) Réinitialisation → 204 (aucun token renvoyé : reconnexion forcée).
        String newPwd = "ResetPass1234";
        String resetBody = "{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"newPassword\":\"" + newPwd + "\"}";
        assertThat(post("/api/auth/reset-password", resetBody)).as("reset-password OK").isEqualTo(204);

        // 4) Le nouveau mot de passe fonctionne, l'ancien non.
        assertThat(login(email, newPwd)).as("login nouveau mot de passe").isEqualTo(200);
        assertThat(login(email, OLD_PWD)).as("ancien mot de passe rejeté").isEqualTo(400);

        // 5) Réutilisation du même code → refusée (usage unique consommé).
        assertThat(post("/api/auth/reset-password", resetBody)).as("code déjà utilisé").isEqualTo(400);

        // Cleanup.
        restTemplate.exchange(url("/api/users/" + userId), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void forgotPassword_unknownEmail_returns202_antiEnumeration() {
        // Anti-énumération : même réponse 202 que pour un compte existant, sans créer d'OTP.
        assertThat(post("/api/auth/forgot-password",
            "{\"email\":\"ghost-" + UUID.randomUUID() + "@x.ma\"}")).isEqualTo(202);
    }

    @Test
    void resetPassword_wrongCode_returns400_andOldPasswordStillWorks() throws Exception {
        String admin = adminBearer();
        String email = "reset-wrong-" + UUID.randomUUID() + "@x.ma";
        String userId = om.readTree(register(email).getBody()).get("id").asText();
        post("/api/auth/forgot-password", "{\"email\":\"" + email + "\"}");

        String resetBody = "{\"email\":\"" + email + "\",\"code\":\"000000\",\"newPassword\":\"ResetPass1234\"}";
        assertThat(post("/api/auth/reset-password", resetBody)).as("mauvais code").isEqualTo(400);
        // Le mot de passe n'a pas changé.
        assertThat(login(email, OLD_PWD)).as("ancien mot de passe encore valide").isEqualTo(200);

        restTemplate.exchange(url("/api/users/" + userId), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void resetPassword_unknownEmail_returns400_generic() {
        // Anti-énumération sur l'étape de vérification : même 400 générique qu'un code faux.
        String body = "{\"email\":\"ghost-" + UUID.randomUUID() + "@x.ma\",\"code\":\"123456\","
            + "\"newPassword\":\"ResetPass1234\"}";
        assertThat(post("/api/auth/reset-password", body)).isEqualTo(400);
    }

    @Test
    void endpoints_arePublic_andValidatePayload() {
        // PUBLIC (permitAll) : on atteint la validation (400/202), jamais 401.
        assertThat(post("/api/auth/forgot-password", "{\"email\":\"\"}"))
            .as("email vide → 400 (pas 401)").isEqualTo(400);
        // Mot de passe < 10 → 400 (NIST min 10).
        String shortPwd = "{\"email\":\"x@y.ma\",\"code\":\"123456\",\"newPassword\":\"short\"}";
        assertThat(post("/api/auth/reset-password", shortPwd)).as("mot de passe trop court → 400").isEqualTo(400);
        // Code non 6-chiffres → 400.
        String badCode = "{\"email\":\"x@y.ma\",\"code\":\"abc\",\"newPassword\":\"ResetPass1234\"}";
        assertThat(post("/api/auth/reset-password", badCode)).as("code invalide format → 400").isEqualTo(400);
    }
}
