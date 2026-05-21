package com.onesley.oneclick.modules.system;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/system} : health-checks, alerts, rules, quotas, custom-roles, documents. */
class SystemFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void system_readEndpoints_200() {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/system/health-checks?limit=10"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/system/health-checks/by-component/database?limit=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/system/alerts?limit=10"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/system/alert-rules"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/system/quota-change-logs?limit=10"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/system/custom-roles"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void document_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/system/documents/inconnu-" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void healthChecks_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/system/health-checks"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
