package com.onesley.oneclick.modules.analytics;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/analytics} vues admin (stats-full, users, wallet, pool, hi-cockpit). */
class AdminViewsFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void adminViews_allAggregates_200() {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/analytics/admin-stats-full?period=mois"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/admin-stats-full"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/admin-users?limit=10"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/admin-wallet/summary"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/admin-wallet/transactions?limit=10"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/recycling-pool"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/admin-hi-cockpit"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminStatsFull_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/analytics/admin-stats-full"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void adminUsers_asClient_403() {
        assertThat(restTemplate.exchange(url("/api/analytics/admin-users?limit=5"), HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
