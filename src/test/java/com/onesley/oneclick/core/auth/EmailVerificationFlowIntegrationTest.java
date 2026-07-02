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

    private ResponseEntity<String> registerWith(String email, String phone, String firstName) {
        String body = "{\"email\":\"" + email + "\",\"phone\":\"" + phone + "\",\"password\":\"password1234\","
            + "\"firstName\":\"" + firstName + "\",\"lastName\":\"Flow\",\"cguAccepted\":true}";
        return restTemplate.exchange(url("/api/users/register"), HttpMethod.POST,
            jsonJwtEntity(body, null), String.class);
    }

    /**
     * Bug « ce numéro / cet email est déjà utilisé » au re-submit : tant que le compte est
     * {@code pending_email_verification} (jamais confirmé par OTP), re-soumettre le formulaire
     * (email/téléphone identiques, un champ corrigé) doit RÉUTILISER le même compte — pas de 409,
     * pas de doublon, {@code id} + {@code referralCode} conservés. Une fois le compte {@code active},
     * la même re-soumission redevient un vrai conflit (409, pas de prise de contrôle).
     */
    @Test
    void resubmitWhilePending_reusesSameAccount_thenConflictsOnceActive() throws Exception {
        String admin = adminBearer();
        String email = "resubmit-" + UUID.randomUUID() + "@x.ma";
        String phone = "06" + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000L);

        // 1) 1re soumission avec une faute → compte pending créé.
        ResponseEntity<String> reg1 = registerWith(email, phone, "Typo");
        assertThat(reg1.getStatusCode()).as("register #1 — body=%s", reg1.getBody()).isEqualTo(HttpStatus.CREATED);
        String userId = om.readTree(reg1.getBody()).get("id").asText();
        assertThat(om.readTree(reg1.getBody()).get("status").asText()).isEqualTo("pending_email_verification");
        String referral1 = jdbc.queryForObject(
            "SELECT referral_code FROM users WHERE id = ?::uuid", String.class, userId);

        // 2) Re-soumission (prénom corrigé, mêmes email + téléphone) → réutilise le compte, PAS de 409.
        ResponseEntity<String> reg2 = registerWith(email, phone, "Fixed");
        assertThat(reg2.getStatusCode()).as("re-submit pending — body=%s", reg2.getBody())
            .isEqualTo(HttpStatus.CREATED);
        assertThat(reg2.getStatusCode()).isNotEqualTo(HttpStatus.CONFLICT);
        assertThat(om.readTree(reg2.getBody()).get("id").asText()).as("même compte").isEqualTo(userId);
        assertThat(om.readTree(reg2.getBody()).get("status").asText()).isEqualTo("pending_email_verification");

        // Invariants : pas de doublon, referralCode conservé (QR déjà affiché), champ corrigé persisté.
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM users WHERE lower(email) = lower(?) AND deleted_at IS NULL", Integer.class, email);
        assertThat(count).as("un seul compte pour cet email").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT referral_code FROM users WHERE id = ?::uuid", String.class, userId))
            .as("referralCode inchangé").isEqualTo(referral1);
        assertThat(jdbc.queryForObject("SELECT first_name FROM users WHERE id = ?::uuid", String.class, userId))
            .as("prénom corrigé persisté").isEqualTo("Fixed");

        // 3) L'OTP confirme et active le compte réutilisé (le flux normal continue de marcher).
        String otpReqBody = "{\"email\":\"" + email + "\",\"purpose\":\"signup\"}";
        String otpId = om.readTree(restTemplate.exchange(url("/api/auth/otp/request"), HttpMethod.POST,
            jsonJwtEntity(otpReqBody, null), String.class).getBody()).get("otpId").asText();
        String code = String.valueOf(jdbc.queryForMap(
            "SELECT code FROM otp_requests WHERE id = ?::uuid", otpId).get("code"));
        String verifyBody = "{\"otpId\":\"" + otpId + "\",\"code\":\"" + code + "\"}";
        ResponseEntity<String> verify = restTemplate.exchange(url("/api/auth/otp/verify"), HttpMethod.POST,
            jsonJwtEntity(verifyBody, null), String.class);
        assertThat(verify.getStatusCode()).as("otp/verify — body=%s", verify.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForMap("SELECT status FROM users WHERE id = ?::uuid", userId).get("status"))
            .isEqualTo("active");

        // 4) Le compte est maintenant ACTIF → re-soumettre le même email est un vrai conflit (409).
        ResponseEntity<String> reg3 = registerWith(email, phone, "Again");
        assertThat(reg3.getStatusCode()).as("re-submit après activation — body=%s", reg3.getBody())
            .isEqualTo(HttpStatus.CONFLICT);

        restTemplate.exchange(url("/api/users/" + userId), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    /**
     * SÉCURITÉ E2E — non-régression du vol de compte. Un attaquant anonyme envoyant
     * {email LIBRE + téléphone d'une victime en attente de vérification} ne doit PAS reprendre le
     * compte de la victime : l'ancre de reprise est l'email (ici libre) → 409, et l'email + le hash
     * du mot de passe + le statut de la victime restent STRICTEMENT inchangés (pas de réécriture de
     * credentials, donc l'attaquant ne peut pas activer via OTP puis se connecter).
     */
    @Test
    void resubmitWithVictimPhoneAndFreeEmail_isRejected_andVictimAccountUntouched() throws Exception {
        String admin = adminBearer();
        String victimEmail = "victim-" + UUID.randomUUID() + "@x.ma";
        String victimPhone = "06" + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000L);

        // Victime : inscription non encore vérifiée (pending).
        ResponseEntity<String> vreg = registerWith(victimEmail, victimPhone, "Victim");
        assertThat(vreg.getStatusCode()).as("register victime — body=%s", vreg.getBody()).isEqualTo(HttpStatus.CREATED);
        String victimId = om.readTree(vreg.getBody()).get("id").asText();
        Map<String, Object> before = jdbc.queryForMap(
            "SELECT email, password_hash, status FROM users WHERE id = ?::uuid", victimId);

        // Attaquant : email libre + téléphone de la victime + mot de passe choisi → doit être REJETÉ (409).
        ResponseEntity<String> attack = registerWith(
            "attacker-" + UUID.randomUUID() + "@evil.com", victimPhone, "ATTACKER");
        assertThat(attack.getStatusCode()).as("attaque takeover — body=%s", attack.getBody())
            .isEqualTo(HttpStatus.CONFLICT);

        // Le compte de la victime est intact : email, hash mot de passe et statut inchangés.
        Map<String, Object> after = jdbc.queryForMap(
            "SELECT email, password_hash, status FROM users WHERE id = ?::uuid", victimId);
        assertThat(after.get("email")).as("email victime inchangé").isEqualTo(before.get("email"));
        assertThat(after.get("password_hash")).as("mot de passe victime NON réécrit").isEqualTo(before.get("password_hash"));
        assertThat(after.get("status")).as("statut victime inchangé").isEqualTo("pending_email_verification");

        restTemplate.exchange(url("/api/users/" + victimId), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }
}
