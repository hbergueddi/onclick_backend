package com.onesley.oneclick.modules.social;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E Sprint H — SocialExtensionController (elite-applications + restaurant-groups).
 */
class SocialExtensionSmokeIntegrationTests extends AbstractIntegrationTest {

    private static final UUID BOGUS = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Test
    void eliteApplications_admin_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/social/elite-applications"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void eliteApplications_byUser_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/social/elite-applications/by-user/" + BOGUS),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void restaurantGroups_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/restaurant-groups"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
