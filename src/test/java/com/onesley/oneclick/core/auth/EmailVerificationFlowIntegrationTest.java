package com.onesley.oneclick.core.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 enrollment — flux complet de vérification email obligatoire au signup (OTP 2 étapes),
 * sur la stack réelle avec {@code app.auth.email-verification-required=true} :
 *
 * <ol>
 *   <li>register public → compte créé en {@code pending_email_verification} (signal dans la réponse)</li>
 *   <li>login → 403 {@code email-not-verified} (gaté après vérif mot de passe)</li>
 *   <li>otp/request signup → 202 + code persisté (livraison email via OtpEmailListener, stub-safe en test)</li>
 *   <li>otp/verify → 200, compte basculé {@code active}</li>
 *   <li>login → 200 (session émise)</li>
 * </ol>
 *
 * <p>{@code @TestPropertySource} crée un contexte dédié avec le flag ON sans impacter les autres
 * tests d'intégration (défaut false = rétro-compat).</p>
 */
@TestPropertySource(properties = "app.auth.email-verification-required=true")
class EmailVerificationFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private ResponseEntity<String> register(String email) {
        String body = "{\"email\":\"" + email + "\",\"password\":\"password1234\","
            + "\"firstName\":\"Otp\",\"lastName\":\"Flow\",\"cguAccepted\":true}";
        return restTemplate.exchange(url("/api/users/register"), HttpMethod.POST,
            jsonJwtEntity(body, null), String.class);
    }

    private ResponseEntity<String> login(String email) {
        String body = "{\"email\":\"" + email + "\",\"password\":\"password1234\"}";
        return restTemplate.exchange(url("/api/auth/login"), HttpMethod.POST,
            jsonJwtEntity(body, null), String.class);
    }

    @Test
    void signup_thenOtpVerify_thenLogin_endToEnd() throws Exception {
        String admin = adminBearer();
        String email = "otpflow-" + UUID.randomUUID() + "@x.ma";

        // 1) Register → pending_email_verification (le statut renvoyé sert de signal au client).
        ResponseEntity<String> reg = register(email);
        assertThat(reg.getStatusCode()).as("register — body=%s", reg.getBody()).isEqualTo(HttpStatus.CREATED);
        String userId = om.readTree(reg.getBody()).get("id").asText();
        assertThat(om.readTree(reg.getBody()).get("status").asText())
            .isEqualTo("pending_email_verification");

        // 2) Login refusé tant que l'email n'est pas vérifié (403, slug email-not-verified).
        ResponseEntity<String> blocked = login(email);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(blocked.getBody()).as("ProblemDetail type discriminant").contains("email-not-verified");

        // 3) Demande OTP signup → 202 + code persisté en base (l'email part via listener stub-safe).
        String otpReqBody = "{\"email\":\"" + email + "\",\"purpose\":\"signup\"}";
        ResponseEntity<String> otpReq = restTemplate.exchange(url("/api/auth/otp/request"), HttpMethod.POST,
            jsonJwtEntity(otpReqBody, null), String.class);
        assertThat(otpReq.getStatusCode()).as("otp/request — body=%s", otpReq.getBody()).isEqualTo(HttpStatus.ACCEPTED);
        String otpId = om.readTree(otpReq.getBody()).get("otpId").asText();

        Map<String, Object> otpRow = jdbc.queryForMap("SELECT code FROM otp_requests WHERE id = ?::uuid", otpId);
        String code = String.valueOf(otpRow.get("code"));
        assertThat(code).matches("\\d{6}");

        // 4) Vérification OTP → 200, compte activé.
        String verifyBody = "{\"otpId\":\"" + otpId + "\",\"code\":\"" + code + "\"}";
        ResponseEntity<String> verify = restTemplate.exchange(url("/api/auth/otp/verify"), HttpMethod.POST,
            jsonJwtEntity(verifyBody, null), String.class);
        assertThat(verify.getStatusCode()).as("otp/verify — body=%s", verify.getBody()).isEqualTo(HttpStatus.OK);

        Map<String, Object> userRow = jdbc.queryForMap("SELECT status FROM users WHERE id = ?::uuid", userId);
        assertThat(userRow.get("status")).as("compte activé après vérif email").isEqualTo("active");

        // 5) Login désormais autorisé → 200 + session.
        ResponseEntity<String> ok = login(email);
        assertThat(ok.getStatusCode()).as("login post-vérif — body=%s", ok.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(ok.getBody()).get("accessToken").asText()).isNotBlank();

        // Cleanup.
        restTemplate.exchange(url("/api/users/" + userId), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void otpVerify_wrongCode_keepsAccountPending() throws Exception {
        String admin = adminBearer();
        String email = "otpwrong-" + UUID.randomUUID() + "@x.ma";

        String userId = om.readTree(register(email).getBody()).get("id").asText();

        String otpReqBody = "{\"email\":\"" + email + "\",\"purpose\":\"signup\"}";
        restTemplate.exchange(url("/api/auth/otp/request"), HttpMethod.POST,
            jsonJwtEntity(otpReqBody, null), String.class);

        // Mauvais code → 400, le compte reste pending.
        String otpId = jdbc.queryForObject(
            "SELECT id FROM otp_requests WHERE user_id = ?::uuid ORDER BY created_at DESC LIMIT 1",
            String.class, userId);
        String verifyBody = "{\"otpId\":\"" + otpId + "\",\"code\":\"000000\"}";
        ResponseEntity<String> verify = restTemplate.exchange(url("/api/auth/otp/verify"), HttpMethod.POST,
            jsonJwtEntity(verifyBody, null), String.class);
        assertThat(verify.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> userRow = jdbc.queryForMap("SELECT status FROM users WHERE id = ?::uuid", userId);
        assertThat(userRow.get("status")).isEqualTo("pending_email_verification");

        restTemplate.exchange(url("/api/users/" + userId), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }
}
