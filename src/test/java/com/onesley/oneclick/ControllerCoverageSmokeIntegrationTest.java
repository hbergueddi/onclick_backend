package com.onesley.oneclick;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comble les controllers sans smoke test + cas limites (option exhaustive).
 *
 * <p>Pour chaque controller jusqu'ici non couvert : happy-path autorisé (200) et
 * refus anonyme (401), sauf endpoints publics (Explore featured, Search) testés
 * sans JWT. Cas limites : 404 (ressource absente), 403 (rôle insuffisant), 400
 * (paramètre requis manquant).
 */
class ControllerCoverageSmokeIntegrationTest extends AbstractIntegrationTest {

    private int get(String path, String jwt) {
        return restTemplate.exchange(url(path), HttpMethod.GET, jwtEntity(jwt), String.class)
            .getStatusCode().value();
    }

    private String aRestaurantId() {
        return jdbc.queryForObject(
            "SELECT id::text FROM restaurants WHERE deleted_at IS NULL LIMIT 1", String.class);
    }

    // ─── Controllers protégés : 200 autorisé / 401 anonyme ─────────────────────

    @Test
    void analyticsAdminStats_authorized200_anon401() {
        assertThat(get("/api/analytics/admin-stats", adminBearer())).isEqualTo(200);
        assertThat(get("/api/analytics/admin-stats", null)).isEqualTo(401);
    }

    @Test
    void communityPosts_authorized200_anon401() {
        assertThat(get("/api/community/posts?page=0&size=5", adminBearer())).isEqualTo(200);
        assertThat(get("/api/community/posts?page=0&size=5", null)).isEqualTo(401);
    }

    @Test
    void configurationFeatureFlags_authorized200_anon401() {
        assertThat(get("/api/configuration/feature-flags", adminBearer())).isEqualTo(200);
        assertThat(get("/api/configuration/feature-flags", null)).isEqualTo(401);
    }

    @Test
    void events_authorized200_anon401() {
        assertThat(get("/api/events?page=0&size=5", adminBearer())).isEqualTo(200);
        assertThat(get("/api/events?page=0&size=5", null)).isEqualTo(401);
    }

    @Test
    void media_authorized200_anon401() {
        assertThat(get("/api/media?page=0&size=5", adminBearer())).isEqualTo(200);
        assertThat(get("/api/media?page=0&size=5", null)).isEqualTo(401);
    }

    @Test
    void supportTickets_authorized200_anon401() {
        assertThat(get("/api/support/tickets?page=0&size=5", adminBearer())).isEqualTo(200);
        assertThat(get("/api/support/tickets?page=0&size=5", null)).isEqualTo(401);
    }

    @Test
    void enrollmentsByRestaurant_authorized200_anon401() {
        String path = "/api/loyalty/enrollments/by-restaurant/" + aRestaurantId();
        assertThat(get(path, adminBearer())).isEqualTo(200);
        assertThat(get(path, null)).isEqualTo(401);
    }

    @Test
    void walletPass_anon401_andAuthorizedNotDenied() {
        assertThat(get("/api/loyalty/wallet-pass?platform=apple", null)).isEqualTo(401);
        // business result dépend des données/certs : on prouve juste l'auth (pas 401/403)
        assertThat(get("/api/loyalty/wallet-pass?platform=apple", adminBearer())).isNotIn(401, 403);
    }

    // ─── Endpoints PUBLICS : 200 sans JWT ──────────────────────────────────────

    @Test
    void exploreFeatured_public200() {
        assertThat(get("/api/restaurants/featured", null)).isEqualTo(200);
    }

    @Test
    void searchRestaurants_public200() {
        assertThat(get("/api/search/restaurants?q=pizza", null)).isEqualTo(200);
    }

    // ─── Cas limites ───────────────────────────────────────────────────────────

    @Test
    void getById_randomUuid_returns404() {
        UUID rnd = UUID.randomUUID();
        assertThat(get("/api/restaurants/" + rnd, adminBearer())).isEqualTo(404);
        assertThat(get("/api/events/" + rnd, adminBearer())).isEqualTo(404);
        assertThat(get("/api/support/tickets/" + rnd, adminBearer())).isEqualTo(404);
    }

    @Test
    void protectedRoutes_clientForbidden_returns403() {
        // Routes strictement admin que le rôle CLIENT n'a pas (VIEW:ANALYTICS, VIEW:ROLES).
        // NB : CLIENT possède bien VIEW:SUPPORT (gère ses propres tickets) — donc /api/support
        // n'est PAS un cas de refus pour CLIENT, c'est intentionnel côté seed RBAC.
        assertThat(get("/api/analytics/admin-stats", bearerForRole("CLIENT"))).isEqualTo(403);
        assertThat(get("/api/roles", bearerForRole("CLIENT"))).isEqualTo(403);
    }

    @Test
    void search_missingRequiredQueryParam_returns400() {
        assertThat(get("/api/search/restaurants", null)).isEqualTo(400);
    }
}
