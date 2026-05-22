package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — enrollments + wallet-pass (lectures déterministes + garde auth). */
class EnrollmentWalletFlowIntegrationTest extends AbstractIntegrationTest {

    private String restaurantId() { return jdbc.queryForObject("SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void enrollments_byRestaurant_200() {
        assertThat(restTemplate.exchange(url("/api/loyalty/enrollments/by-restaurant/" + restaurantId()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void walletPass_metadata_200() {
        assertThat(restTemplate.exchange(url("/api/loyalty/wallet-pass/metadata"),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void enrollments_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/loyalty/enrollments/by-restaurant/" + restaurantId()),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void walletPass_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/loyalty/wallet-pass/metadata"),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
