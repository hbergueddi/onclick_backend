package com.onesley.oneclick.core.auth.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A3 — purge des comptes restés {@code pending_email_verification} (anti-bot signup).
 *
 * <p>Flag {@code email-verification-required=true} + TTL 24 h. On crée deux comptes pending :
 * l'un antidaté au-delà du TTL (+ un OTP), l'autre récent. Après {@code purgeStalePendingSignups()},
 * seul l'ancien (et ses OTP) doit avoir disparu.
 */
@TestPropertySource(properties = {
    "app.auth.email-verification-required=true",
    "app.auth.pending-verification-ttl-hours=24"
})
class PendingSignupPurgeJobIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    @Autowired
    private PendingSignupPurgeJob purgeJob;

    private String register(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"password1234\","
            + "\"firstName\":\"Purge\",\"lastName\":\"Test\",\"cguAccepted\":true}";
        ResponseEntity<String> reg = restTemplate.exchange(url("/api/users/register"), HttpMethod.POST,
            jsonJwtEntity(body, null), String.class);
        assertThat(reg.getStatusCode()).as("register — body=%s", reg.getBody()).isEqualTo(HttpStatus.CREATED);
        assertThat(om.readTree(reg.getBody()).get("status").asText()).isEqualTo("pending_email_verification");
        return om.readTree(reg.getBody()).get("id").asText();
    }

    private void requestOtp(String email) {
        String body = "{\"email\":\"" + email + "\",\"purpose\":\"signup\"}";
        restTemplate.exchange(url("/api/auth/otp/request"), HttpMethod.POST,
            jsonJwtEntity(body, null), String.class);
    }

    @Test
    void purge_removesStalePendingAndItsOtp_keepsRecentPending() throws Exception {
        String staleEmail = "purge-stale-" + UUID.randomUUID() + "@x.ma";
        String recentEmail = "purge-recent-" + UUID.randomUUID() + "@x.ma";

        String staleId = register(staleEmail);
        requestOtp(staleEmail); // crée au moins une ligne otp_requests (FK à purger)
        String recentId = register(recentEmail);

        // Antidater le compte "stale" au-delà du TTL (48 h) ; le récent reste à "now".
        int updated = jdbc.update("UPDATE users SET created_at = ? WHERE id = ?::uuid",
            Timestamp.from(Instant.now().minus(48, ChronoUnit.HOURS)), staleId);
        assertThat(updated).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM otp_requests WHERE user_id = ?::uuid", Integer.class, staleId))
            .as("OTP créé pour le compte stale").isGreaterThanOrEqualTo(1);

        // Act.
        purgeJob.purgeStalePendingSignups();

        // Le compte stale + ses OTP ont disparu.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?::uuid", Integer.class, staleId))
            .as("compte pending antidaté supprimé").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM otp_requests WHERE user_id = ?::uuid", Integer.class, staleId))
            .as("OTP du compte supprimé purgés").isZero();
        // L'email est libéré (ré-inscription possible) : plus aucune ligne avec cet email.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, staleEmail))
            .as("email libéré").isZero();

        // Le compte pending récent survit.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?::uuid", Integer.class, recentId))
            .as("compte pending récent conservé").isEqualTo(1);

        // Cleanup.
        String admin = adminBearer();
        restTemplate.exchange(url("/api/users/" + recentId), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }
}
