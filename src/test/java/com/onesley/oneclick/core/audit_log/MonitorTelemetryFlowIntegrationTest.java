package com.onesley.oneclick.core.audit_log;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/audit/telemetry} : lecture admin + garde auth. */
class MonitorTelemetryFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void telemetry_read_200() {
        assertThat(restTemplate.exchange(url("/api/audit/telemetry?limit=10"), HttpMethod.GET, jwtEntity(adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void telemetry_read_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/audit/telemetry?limit=10"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
