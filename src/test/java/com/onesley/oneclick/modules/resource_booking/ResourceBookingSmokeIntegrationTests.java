package com.onesley.oneclick.modules.resource_booking;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E pour {@code modules/resource_booking} — listing ressources bookables.
 */
class ResourceBookingSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void getResources_paginated_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/resource-bookings/resources?page=0&size=5"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
