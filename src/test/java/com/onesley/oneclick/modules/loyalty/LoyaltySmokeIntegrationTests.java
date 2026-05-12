package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E pour {@code modules/loyalty} — comptes par client.
 */
class LoyaltySmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void getAccountsByClient_returnsList() {
        // SUPERADMIN admin → SecurityHelper.requireOwnerOrAdmin passe.
        // Client UUID inexistant → liste vide JSON, status 200.
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/loyalty/accounts/by-client/00000000-0000-0000-0000-000000000099"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
