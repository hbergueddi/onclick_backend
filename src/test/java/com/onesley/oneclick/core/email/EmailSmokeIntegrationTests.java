package com.onesley.oneclick.core.email;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E Sprint I.3 — EmailController (Resend wrapper stub mode).
 */
class EmailSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void sendEmail_stubMode_returns202() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminBearer());
        String body = """
            {
              "template": "pcc-enrollment-invite",
              "tenantSlug": "palmeraie",
              "to": ["smoke.test@example.com"],
              "subjectFr": "Bienvenue PCC",
              "variables": {"firstName":"Smoke","link":"https://app-oneclick.net"}
            }
            """;
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/email/send"),
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);
        // En stub mode (pas de api-key Resend) → 202 ACCEPTED avec sent=false
        // En prod (key configurée) → 200 OK avec sent=true
        assertThat(response.getStatusCode().value()).isIn(200, 202);
        assertThat(response.getBody()).contains("\"sent\"");
    }
}
