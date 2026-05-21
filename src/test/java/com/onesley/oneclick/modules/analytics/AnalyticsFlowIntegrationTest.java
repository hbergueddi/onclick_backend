package com.onesley.oneclick.modules.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/analytics} : admin-stats + api-clients/keys/webhooks/deliveries. */
class AnalyticsFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void apiClient_keys_webhooks_flow() throws Exception {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/analytics/admin-stats"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/api-clients?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> cPost = restTemplate.exchange(url("/api/analytics/api-clients"), HttpMethod.POST,
            jsonJwtEntity(Map.of("name", "L4 Client"), admin), String.class);
        assertThat(cPost.getStatusCode().is2xxSuccessful()).isTrue();
        String clientId = om.readTree(cPost.getBody()).get("id").asText();

        assertThat(restTemplate.exchange(url("/api/analytics/api-clients/" + clientId), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // key — keyHash/keyPrefix uniques par run (contrainte d'unicité en DB)
        String rand = UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<String> kPost = restTemplate.exchange(url("/api/analytics/api-keys"), HttpMethod.POST,
            jsonJwtEntity(Map.of("apiClientId", clientId, "keyHash", "h-" + rand, "keyPrefix", "pref-" + rand), admin), String.class);
        assertThat(kPost.getStatusCode().is2xxSuccessful()).isTrue();
        String keyId = om.readTree(kPost.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/analytics/api-clients/" + clientId + "/keys"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/api-keys/" + keyId), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // webhook
        ResponseEntity<String> wPost = restTemplate.exchange(url("/api/analytics/webhooks"), HttpMethod.POST,
            jsonJwtEntity(Map.of("apiClientId", clientId, "url", "https://x/hook"), admin), String.class);
        assertThat(wPost.getStatusCode().is2xxSuccessful()).isTrue();
        String webhookId = om.readTree(wPost.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/analytics/api-clients/" + clientId + "/webhooks"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/webhooks/" + webhookId + "/deliveries?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/analytics/webhooks/" + webhookId), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void apiClient_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/analytics/api-clients/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminStats_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/analytics/admin-stats"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
