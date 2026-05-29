package com.onesley.oneclick.modules.analytics;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E Sprint H — AdminViewsController + AdminStatsFullService.
 */
class AdminViewsSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void adminStatsFull_noPeriod_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/analytics/admin-stats-full"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("totalRestaurants");
    }

    @Test
    void adminStatsFull_mois_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/analytics/admin-stats-full?period=mois"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminUsers_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/analytics/admin-users?limit=5"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminWalletSummary_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/analytics/admin-wallet/summary"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void recyclingPool_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/analytics/recycling-pool"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminHICockpit_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/analytics/admin-hi-cockpit"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void groupDashboard_admin_returns200() {
        // B1 — rollup groupe (BOGUS id → liste 1 entrée zéros). Admin bypass ABAC.
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/analytics/group-dashboard?restaurantIds=00000000-0000-0000-0000-000000000000"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
