package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E Sprint H — LoyaltyExtensionController (ratings, scores, AI usage,
 * restitutions, tier, expired-points-admin, point-distributions).
 */
class LoyaltyExtensionSmokeIntegrationTests extends AbstractIntegrationTest {

    private static final UUID BOGUS = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Test
    void ratings_byUser_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/loyalty/ratings/by-user/" + BOGUS),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void scores_byUser_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/loyalty/scores/by-user/" + BOGUS),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aiUsage_byUser_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/loyalty/ai-usage/by-user/" + BOGUS),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void tierStatus_byRestaurant_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/loyalty/tier-status/by-restaurant/" + BOGUS),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void expiredPointsAdmin_admin_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/loyalty/expired-points/admin?limit=10"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void pointDistributions_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/loyalty/point-distributions?limit=10"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void restitutions_byRestaurant_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/loyalty/restitutions/by-restaurant/" + BOGUS),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void restaurantCreditSummary_admin_returns200() {
        // B2 — agrégat crédit resto (resto vide → 0/0/0 + byMember vide). Admin bypass ABAC.
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/loyalty/restaurant-credit-summary/" + BOGUS),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void walletPass_metadata_admin_returns200() {
        // Auth as admin in JWT => metadata extracts from JWT sub.
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/loyalty/wallet-pass/metadata"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void walletPass_google_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/loyalty/wallet-pass?platform=google"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("saveUrl");
    }

    @Test
    void walletPass_apple_returns200_pkpass() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/loyalty/wallet-pass?platform=apple"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("passTypeIdentifier");
    }
}
