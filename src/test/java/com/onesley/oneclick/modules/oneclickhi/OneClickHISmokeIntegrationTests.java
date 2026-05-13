package com.onesley.oneclick.modules.oneclickhi;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E Sprint H — OneClickHIController (cockpit + invoices + restaurant-hi).
 */
class OneClickHISmokeIntegrationTests extends AbstractIntegrationTest {

    private static final UUID BOGUS = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Test
    void cockpit_admin_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/oneclickhi/cockpit"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("totalInvoices");
    }

    @Test
    void invoices_list_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/oneclickhi/invoices"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void restaurantHI_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/oneclickhi/restaurant-hi/" + BOGUS),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void restaurantHICharts_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/oneclickhi/restaurant-hi/" + BOGUS + "/charts?months=6"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
