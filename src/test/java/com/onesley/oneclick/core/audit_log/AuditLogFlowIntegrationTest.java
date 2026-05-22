package com.onesley.oneclick.core.audit_log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/audit} : logs, events, errors, jobs (lectures + écritures). */
class AuditLogFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void audit_readEndpoints_200() {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/audit/logs?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/audit/events?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/audit/errors?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/audit/jobs?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void logs_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/audit/logs"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logs_asClient_403() {
        assertThat(restTemplate.exchange(url("/api/audit/logs?page=0&size=5"), HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── Écritures jusqu'ici non couvertes au L4 (recordAudit / publishEvent / recordError) ───

    @Test
    void writes_audit_event_error() throws Exception {
        String admin = adminBearer();

        ResponseEntity<String> log = restTemplate.exchange(url("/api/audit/logs"), HttpMethod.POST,
            jsonJwtEntity(Map.of("entityType", "Restaurant", "action", "UPDATE", "entityId", UUID.randomUUID().toString(),
                "diff", Map.of("field", "v"), "ipAddress", "1.2.3.4", "userAgent", "L4"), admin), String.class);
        assertThat(log.getStatusCode().is2xxSuccessful()).isTrue();
        jdbc.update("DELETE FROM audit_logs WHERE id = ?::uuid", UUID.fromString(om.readTree(log.getBody()).get("id").asText()));

        ResponseEntity<String> evt = restTemplate.exchange(url("/api/audit/events"), HttpMethod.POST,
            jsonJwtEntity(Map.of("type", "l4.test", "payload", Map.of("k", "v")), admin), String.class);
        assertThat(evt.getStatusCode().is2xxSuccessful()).isTrue();
        jdbc.update("DELETE FROM system_events WHERE id = ?::uuid", UUID.fromString(om.readTree(evt.getBody()).get("id").asText()));

        ResponseEntity<String> err = restTemplate.exchange(url("/api/audit/errors"), HttpMethod.POST,
            jsonJwtEntity(Map.of("serviceName", "frontend", "message", "L4 error", "severity", "warn"), admin), String.class);
        assertThat(err.getStatusCode().is2xxSuccessful()).isTrue();
        jdbc.update("DELETE FROM error_logs WHERE id = ?::uuid", UUID.fromString(om.readTree(err.getBody()).get("id").asText()));
    }
}
