package com.onesley.oneclick.core.email;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration Gap #4 — webhook Resend public (ingest bounce) + lecture admin (VIEW:USERS).
 *
 * <p>En test, {@code app.email.resend.webhook-secret} est absent → la signature Svix n'est
 * pas vérifiée (mode stub), le webhook accepte le payload. Le contrat de sécurité testé :
 * webhook PUBLIC (pas de JWT) ; lecture {@code /api/email/bounces} admin-only (CLIENT 403).
 */
class EmailBounceRbacIntegrationTest extends AbstractIntegrationTest {

    private String bouncedEmail;

    @AfterEach
    void cleanup() {
        if (bouncedEmail != null) {
            jdbc.update("DELETE FROM email_bounces WHERE email = ?", bouncedEmail);
            bouncedEmail = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void webhook_public_recordsBounce_noJwt() {
        bouncedEmail = "bounce-" + UUID.randomUUID() + "@x.ma";
        String body = "{\"type\":\"email.bounced\",\"data\":{\"to\":[\"" + bouncedEmail
            + "\"],\"bounce\":{\"type\":\"Permanent\"}}}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // PAS de bearer → endpoint public (auth = signature, absente en test → stub accepte).
        ResponseEntity<java.util.Map> resp = restTemplate.exchange(
            url("/api/email/webhooks/resend"), HttpMethod.POST, new HttpEntity<>(body, headers), java.util.Map.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) resp.getBody().get("recorded")).intValue()).isEqualTo(1);

        Integer suppressed = jdbc.queryForObject(
            "SELECT COUNT(*) FROM email_bounces WHERE email = ? AND is_suppressed = true", Integer.class, bouncedEmail);
        assertThat(suppressed).isEqualTo(1);
    }

    @Test
    void admin_listBounces_returns200() {
        int status = restTemplate.exchange(url("/api/email/bounces"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode().value();
        assertThat(status).isEqualTo(200);
    }

    @Test
    void client_listBounces_returns403() {
        int status = restTemplate.exchange(url("/api/email/bounces"),
            HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class).getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }
}
