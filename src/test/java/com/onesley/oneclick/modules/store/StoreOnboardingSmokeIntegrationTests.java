package com.onesley.oneclick.modules.store;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E Sprint I.3 — StoreOnboardingController (workflow demandes inscription).
 */
class StoreOnboardingSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void submitOnboarding_public_returns201() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String body = """
            {
              "restaurantName": "Test Smoke Test",
              "cuisine": "marocaine",
              "city": "Casablanca",
              "ownerFirstName": "Smoke",
              "ownerLastName": "Test",
              "ownerEmail": "smoke.test@example.com"
            }
            """;
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/store/onboarding"),
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"status\":\"pending\"");
    }

    @Test
    void listOnboarding_admin_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/store/onboarding"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listOnboarding_filterByStatus_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/store/onboarding?status=pending"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
