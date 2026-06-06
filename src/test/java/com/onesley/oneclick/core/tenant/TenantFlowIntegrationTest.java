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

        // CREATE (slug unique ; pas d'endpoint DELETE → self-clean jdbc par slug)
        String slug = "l4-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<String> post = restTemplate.exchange(url("/api/tenants"), HttpMethod.POST,
            jsonJwtEntity(Map.of("name", "L4 Tenant", "slug", slug), admin), String.class);
        assertThat(post.getStatusCode().is2xxSuccessful()).isTrue();

        assertThat(restTemplate.exchange(url("/api/tenants/search"), HttpMethod.POST,
            jsonJwtEntity(Map.of("criteria", List.of(), "page", 0, "size", 5), admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        jdbc.update("DELETE FROM tenants WHERE slug = ?", slug);
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

    // ─── C1 portail tenant-admin (SUPERADMIN-only) : PATCH + branding + features ──

    @Test
    void tenantAdmin_update_branding_features_flow() {
        String admin = adminBearer();
        String slug = "c1-" + UUID.randomUUID().toString().substring(0, 8);
        assertThat(restTemplate.exchange(url("/api/tenants"), HttpMethod.POST,
            jsonJwtEntity(Map.of("name", "C1 Tenant", "slug", slug), admin), String.class)
            .getStatusCode().is2xxSuccessful()).isTrue();
        UUID id = UUID.fromString(jdbc.queryForObject("SELECT id::text FROM tenants WHERE slug = ?", String.class, slug));
        try {
            // PATCH nom + statut → 200, reflété.
            ResponseEntity<String> patch = restTemplate.exchange(url("/api/tenants/" + id), HttpMethod.PATCH,
                jsonJwtEntity(Map.of("name", "C1 Renamed", "status", "paused"), admin), String.class);
            assertThat(patch.getStatusCode())
                .as("PATCH tenant — reçu %s, body=%s", patch.getStatusCode(), patch.getBody())
                .isEqualTo(HttpStatus.OK);
            assertThat(patch.getBody()).contains("C1 Renamed").contains("paused");

            // PUT branding → 200 ; GET branding reflète.
            assertThat(restTemplate.exchange(url("/api/tenants/" + id + "/branding"), HttpMethod.PUT,
                jsonJwtEntity(Map.of("primaryColor", "#714B67", "logoUrl", "https://cdn/logo.png",
                    "backgroundColor", "#FDFBF9", "tagline", "Le club", "appNameWin", "My PCC"), admin), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
            // GET branding reflète les nouveaux champs V74 (parité 1:1).
            String brandingBody = restTemplate.exchange(url("/api/tenants/" + id + "/branding"), HttpMethod.GET,
                jwtEntity(admin), String.class).getBody();
            assertThat(brandingBody).contains("#714B67").contains("Le club").contains("My PCC");

            // PUT feature toggle → 200 ; GET features reflète.
            assertThat(restTemplate.exchange(url("/api/tenants/" + id + "/features/boutique"), HttpMethod.PUT,
                jsonJwtEntity(Map.of("enabled", true), admin), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(restTemplate.exchange(url("/api/tenants/" + id + "/features"), HttpMethod.GET,
                jwtEntity(admin), String.class).getBody()).contains("boutique");

            // RBAC : RESTAURATEUR (sans accès TENANTS) → 403 sur PATCH.
            assertThat(restTemplate.exchange(url("/api/tenants/" + id), HttpMethod.PATCH,
                jsonJwtEntity(Map.of("name", "Hack"), bearerForRole("RESTAURATEUR")), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            jdbc.update("DELETE FROM tenant_features WHERE tenant_id = ?", id);
            jdbc.update("DELETE FROM tenant_brandings WHERE tenant_id = ?", id);
            jdbc.update("DELETE FROM tenants WHERE id = ?", id);
        }
    }

    @Test
    void update_noJwt_401() {
        assertThat(restTemplate.exchange(url("/api/tenants/" + UUID.randomUUID()), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("name", "X"), null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
