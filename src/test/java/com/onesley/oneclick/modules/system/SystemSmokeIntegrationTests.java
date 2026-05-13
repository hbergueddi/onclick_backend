package com.onesley.oneclick.modules.system;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E Sprint H — SystemController (health checks, alerts, alert rules, quota logs).
 */
class SystemSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void healthChecks_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/system/health-checks?limit=10"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void alerts_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/system/alerts?limit=10"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void alertRules_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/system/alert-rules"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void quotaChangeLogs_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/system/quota-change-logs?limit=10"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
