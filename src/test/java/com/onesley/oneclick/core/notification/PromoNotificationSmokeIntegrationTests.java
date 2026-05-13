package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E Sprint H — PromoNotificationController (workflow admin approval push promo).
 */
class PromoNotificationSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void promoRequests_list_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/notifications/promo-requests"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void promoStats_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/notifications/promo-requests/stats"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("totalOffers");
    }
}
