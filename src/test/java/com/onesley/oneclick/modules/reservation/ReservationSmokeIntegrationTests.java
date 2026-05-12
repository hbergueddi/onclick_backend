package com.onesley.oneclick.modules.reservation;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E pour {@code modules/reservation} — recherche dynamique paginée.
 */
class ReservationSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void searchReservations_emptyCriteria_returns200() {
        // SearchRequest minimal : pas de critère + size 3.
        String body = "{\"criteria\":[],\"size\":3}";
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/reservations/search"),
            org.springframework.http.HttpMethod.POST,
            jsonJwtEntity(body, adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
