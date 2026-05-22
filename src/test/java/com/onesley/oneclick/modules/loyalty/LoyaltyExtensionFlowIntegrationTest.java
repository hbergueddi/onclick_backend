package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/loyalty} extensions : ratings/scores/ai-usage/restitutions/tier-status/distributions. */
class LoyaltyExtensionFlowIntegrationTest extends AbstractIntegrationTest {

    private String userId() { return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class); }
    private String restaurantId() { return jdbc.queryForObject("SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void reads_200() {
        String admin = adminBearer();
        String uid = userId(), rid = restaurantId();
        for (String path : new String[]{
            "/api/loyalty/ratings/by-user/" + uid,
            "/api/loyalty/scores/by-user/" + uid,
            "/api/loyalty/ai-usage/by-user/" + uid,
            "/api/loyalty/restitutions/by-restaurant/" + rid,
            "/api/loyalty/tier-status/by-restaurant/" + rid,
            "/api/loyalty/expired-points/admin",
            "/api/loyalty/point-distributions"}) {
            assertThat(restTemplate.exchange(url(path), HttpMethod.GET, jwtEntity(admin), String.class)
                .getStatusCode()).as(path).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void aiUsage_increment_2xx() {
        assertThat(restTemplate.exchange(url("/api/loyalty/ai-usage/by-user/" + userId() + "/increment"),
            HttpMethod.POST, jwtEntity(adminBearer()), String.class).getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void reads_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/loyalty/point-distributions"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
