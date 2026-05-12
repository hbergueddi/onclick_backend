package com.onesley.oneclick.core.tenant;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke E2E pour {@code core/tenant} — liste tous tenants + lookup par slug (public).
 */
class TenantSmokeIntegrationTests extends AbstractIntegrationTest {

    @Test
    void getTenants_returns200() {
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/tenants"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getTenantBySlug_palmeraie_returns200() {
        // L'endpoint n'a pas de @PreAuthorize mais notre filter chain en
        // oauth2.enabled=true exige un Bearer pour anyRequest. On en envoie un.
        ResponseEntity<String> response = restTemplate.exchange(
            url("/api/tenants/by-slug?slug=palmeraie"),
            HttpMethod.GET,
            jwtEntity(adminBearer()),
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("palmeraie");
    }
}
