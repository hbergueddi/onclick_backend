package com.onesley.oneclick.core.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/configuration} : feature flags + targets + cache configs. */
class ConfigurationFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String userId() { return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class); }

    @Test
    void configuration_fullFlow() throws Exception {
        String admin = adminBearer();
        String code = "l4-" + UUID.randomUUID().toString().substring(0, 8);

        ResponseEntity<String> post = restTemplate.exchange(url("/api/configuration/feature-flags"), HttpMethod.POST,
            jsonJwtEntity(Map.of("code", code, "name", "L4 Flag", "enabled", true, "rolloutPct", 50), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();
        String id = om.readTree(post.getBody()).get("id").asText();

        assertThat(restTemplate.exchange(url("/api/configuration/feature-flags"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/configuration/feature-flags/by-code/" + code), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> patch = restTemplate.exchange(url("/api/configuration/feature-flags/" + id), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("enabled", false), admin), String.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);

        // targets
        assertThat(restTemplate.exchange(url("/api/configuration/feature-flag-targets"), HttpMethod.POST,
            jsonJwtEntity(Map.of("featureFlagId", id, "targetType", "user", "targetId", userId(), "enabled", true), admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(restTemplate.exchange(url("/api/configuration/feature-flags/" + id + "/targets"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // cache configs
        assertThat(restTemplate.exchange(url("/api/configuration/cache-configs"), HttpMethod.POST,
            jsonJwtEntity(Map.of("cacheName", "l4-" + UUID.randomUUID().toString().substring(0, 8), "ttlSeconds", 60, "maxEntries", 100), admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(restTemplate.exchange(url("/api/configuration/cache-configs"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void featureFlagByCode_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/configuration/feature-flags/by-code/inconnu-" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_invalidBody_400() {
        assertThat(restTemplate.exchange(url("/api/configuration/feature-flags"), HttpMethod.POST,
            jsonJwtEntity(Map.of("name", "sans code"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_asClient_403() {
        assertThat(restTemplate.exchange(url("/api/configuration/feature-flags"), HttpMethod.POST,
            jsonJwtEntity(Map.of("code", "l4x", "name", "X"), bearerForRole("CLIENT")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
