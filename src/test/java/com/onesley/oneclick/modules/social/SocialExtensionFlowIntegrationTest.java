package com.onesley.oneclick.modules.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/social/elite-applications} + {@code /api/restaurant-groups}. */
class SocialExtensionFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String userId() { return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class); }
    private String tenantId() { return jdbc.queryForObject("SELECT tenant_id::text FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1", String.class); }

    @Test
    void eliteApplications_flow() throws Exception {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/social/elite-applications?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/social/elite-applications/by-user/" + userId()), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> post = restTemplate.exchange(url("/api/social/elite-applications"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", userId(), "motivation", "Candidature L4"), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/social/elite-applications/" + id + "/review"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "approved", "reviewedBy", userId()), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void restaurantGroups_crud() throws Exception {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/restaurant-groups?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> post = restTemplate.exchange(url("/api/restaurant-groups"), HttpMethod.POST,
            jsonJwtEntity(Map.of("tenantId", tenantId(), "name", "Groupe Resto L4", "ownerId", userId()), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/restaurant-groups/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/restaurant-groups/" + id), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("name", "Groupe Resto L4 renommé"), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/restaurant-groups/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void restaurantGroup_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/restaurant-groups/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
