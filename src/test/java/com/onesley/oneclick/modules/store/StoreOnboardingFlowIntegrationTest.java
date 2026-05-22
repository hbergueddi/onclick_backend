package com.onesley.oneclick.modules.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/store/onboarding} : list/create/get/decision. */
class StoreOnboardingFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String tenantId() { return jdbc.queryForObject("SELECT tenant_id::text FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1", String.class); }
    private String userId() { return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void onboarding_fullFlow() throws Exception {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/store/onboarding"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> post = restTemplate.exchange(url("/api/store/onboarding"), HttpMethod.POST,
            jsonJwtEntity(Map.of("tenantId", tenantId(), "restaurantName", "Resto L4 Onboarding", "city", "Casablanca",
                "ownerFirstName", "L4", "ownerLastName", "Owner", "ownerEmail", "l4-" + UUID.randomUUID().toString().substring(0, 8) + "@x.ma"), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/store/onboarding/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> decision = restTemplate.exchange(url("/api/store/onboarding/" + id + "/decision"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "approved", "reviewedBy", userId()), admin), String.class);
        assertThat(decision.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void onboarding_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/store/onboarding/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_invalidBody_400() {
        assertThat(restTemplate.exchange(url("/api/store/onboarding"), HttpMethod.POST,
            jsonJwtEntity(Map.of("city", "Casablanca"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
