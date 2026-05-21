package com.onesley.oneclick.core.audit_log;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/audit} : logs, events, errors, jobs (lectures paginées). */
class AuditLogFlowIntegrationTest extends AbstractIntegrationTest {

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
}
