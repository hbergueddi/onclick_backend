package com.onesley.oneclick.core.tenant;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** L4 « profondeur » — {@code /api/tenants} : list/get/by-slug/create/search + erreurs. */
class TenantFlowIntegrationTest extends AbstractIntegrationTest {

    private String existingTenantId() { return jdbc.queryForObject("SELECT id::text FROM tenants LIMIT 1", String.class); }
    private String existingSlug() { return jdbc.queryForObject("SELECT slug FROM tenants WHERE slug IS NOT NULL LIMIT 1", String.class); }

    @Test
    void tenant_readAndCreateFlow() {
        String admin = adminBearer();
        assertThat(restTemplate.exchange(url("/api/tenants?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/tenants/" + existingTenantId()), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/tenants/by-slug?slug=" + existingSlug()), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // CREATE (slug unique, pas d'endpoint delete → laissé)
        ResponseEntity<String> post = restTemplate.exchange(url("/api/tenants"), HttpMethod.POST,
            jsonJwtEntity(Map.of("name", "L4 Tenant", "slug", "l4-" + UUID.randomUUID().toString().substring(0, 8)), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(restTemplate.exchange(url("/api/tenants/search"), HttpMethod.POST,
            jsonJwtEntity(Map.of("criteria", List.of(), "page", 0, "size", 5), admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getById_unknown_404() {
        assertThat(restTemplate.exchange(url("/api/tenants/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_invalidSlug_400() {
        assertThat(restTemplate.exchange(url("/api/tenants"), HttpMethod.POST,
            jsonJwtEntity(Map.of("name", "X", "slug", "INVALID SLUG!"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_asClient_403() {
        assertThat(restTemplate.exchange(url("/api/tenants"), HttpMethod.POST,
            jsonJwtEntity(Map.of("name", "X", "slug", "l4x"), bearerForRole("CLIENT")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
