package com.onesley.oneclick.modules.restaurant;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E pour {@code modules/restaurant} + recherche /api/search/restaurants/by-city.
 */
class RestaurantSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void getRestaurants_paginated_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/restaurants?page=0&size=5"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void searchByCity_casablanca_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/search/restaurants/by-city?city=Casablanca&limit=2"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
