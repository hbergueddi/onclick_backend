package com.onesley.oneclick.modules.financial;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E pour {@code modules/financial} — liste paginée des contrats.
 */
class FinancialSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void getContracts_paginated_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/financial/contracts?page=0&size=5"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void walletBalances_batch_returns200() {
        // B1.5 — batch (BOGUS id → liste vide). Admin bypass ABAC.
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/financial/wallet/balances?restaurantIds=00000000-0000-0000-0000-000000000000"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
