package com.onesley.oneclick.modules.payment;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E pour {@code modules/payment} — méthodes de paiement par user.
 */
class PaymentSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void getMethodsByUser_returns200() {
        // SUPERADMIN admin → SecurityHelper.requireOwnerOrAdmin passe.
        // UUID inconnu → service renvoie liste vide (pas 404).
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/payments/methods/by-user/00000000-0000-0000-0000-000000000099"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
