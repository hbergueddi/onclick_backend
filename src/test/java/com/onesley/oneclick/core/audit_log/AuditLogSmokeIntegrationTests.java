package com.onesley.oneclick.core.audit_log;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E pour {@code core/audit_log} — listing logs + events système.
 */
class AuditLogSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void getAuditLogs_paginated_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/audit/logs?page=0&size=5"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getSystemEvents_paginated_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/audit/events?page=0&size=5"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
