package com.onesley.oneclick.modules.promotion;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E pour {@code modules/promotion} — liste paginée des offres.
 */
class OfferSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void getOffers_paginated_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/offers?page=0&size=5"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
