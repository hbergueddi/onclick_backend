package com.onesley.oneclick.core.audit_log;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E Sprint I.3 — MonitorTelemetryController (ingestion batch + listing).
 */
class MonitorTelemetrySmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void ingestTelemetry_publicBatch_returns201() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
            {
              "events": [
                {"eventType":"app_startup","platform":"ios","appVersion":"1.0.0"},
                {"eventType":"push_register","platform":"android","eventData":{"token":"abc"}}
              ]
            }
            """;
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/audit/telemetry"),
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"ingested\":2");
    }

    @Test
    void ingestTelemetry_emptyEvents_returns400() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/audit/telemetry"),
            HttpMethod.POST,
            new HttpEntity<>("{\"events\":[]}", headers),
            String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listTelemetry_admin_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/audit/telemetry?limit=10"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
