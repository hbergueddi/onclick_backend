package com.onesley.oneclick.modules.reservation;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC de la ressource {@code DISPUTES} (Feature #3) sur la stack de
 * sécurité réelle (filter chain → JwtDecoder → UserRoleAuthoritiesConverter →
 * @PreAuthorize → service → repo → DB). Calque
 * {@code LoyaltyTierRuleControllerRbacIntegrationTest}.
 *
 * <p>Matrice (cf V62) :
 * <ul>
 *   <li>CREATE:DISPUTES → CLIENT, SUPERADMIN</li>
 *   <li>VIEW:DISPUTES   → CLIENT, RESTAURATEUR, GROUP_ADMIN, SUPERADMIN, (STAFF)</li>
 *   <li>UPDATE:DISPUTES → RESTAURATEUR, GROUP_ADMIN, SUPERADMIN, (STAFF)</li>
 * </ul>
 *
 * <p>On vérifie l'absence d'autorité = 403 (RBAC) et la présence = passage du gate
 * (≠ 403). Les rôles testés sont ceux qui ont des users seedés dans
 * {@code oneclick_enterprise} : SUPERADMIN, RESTAURATEUR, CLIENT, GROUP_ADMIN.
 * Le scoping fin (ownership/phase) est couvert par {@code NoShowDisputeFlowIntegrationTest}.
 */
class DisputeControllerRbacIntegrationTest extends AbstractIntegrationTest {

    // ─── CREATE:DISPUTES ──────────────────────────────────────────────────────────

    /** RESTAURATEUR n'a pas CREATE:DISPUTES → 403 (RBAC, avant tout ABAC). */
    @Test
    void create_restaurateur_returns403_noCreateAuthority() {
        int status = restTemplate.exchange(
            url("/api/reservations/" + UUID.randomUUID() + "/disputes"), HttpMethod.POST,
            jsonJwtEntity(Map.of("reason", "x"), bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    /** CLIENT a CREATE:DISPUTES → le gate RBAC passe ; résa inexistante → 404 (≠ 403). */
    @Test
    void create_client_passesRbac_thenNotFound() {
        int status = restTemplate.exchange(
            url("/api/reservations/" + UUID.randomUUID() + "/disputes"), HttpMethod.POST,
            jsonJwtEntity(Map.of("reason", "je conteste"), bearerForRole("CLIENT")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(404); // RBAC OK → NotFound sur la résa random
    }

    // ─── UPDATE:DISPUTES ──────────────────────────────────────────────────────────

    /** CLIENT n'a pas UPDATE:DISPUTES → 403 (RBAC). */
    @Test
    void resolve_client_returns403_noUpdateAuthority() {
        int status = restTemplate.exchange(
            url("/api/disputes/" + UUID.randomUUID()), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "accepted"), bearerForRole("CLIENT")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    /** RESTAURATEUR a UPDATE:DISPUTES → le gate RBAC passe ; dispute inexistante → 404 (≠ 403). */
    @Test
    void resolve_restaurateur_passesRbac_thenNotFound() {
        int status = restTemplate.exchange(
            url("/api/disputes/" + UUID.randomUUID()), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "refused"), bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(404);
    }

    // ─── VIEW:DISPUTES ──────────────────────────────────────────────────────────

    /** CLIENT a VIEW:DISPUTES → dashboard scopé (non-staff → liste vide) → 200. */
    @Test
    void list_client_returns200_scopedEmpty() {
        int status = restTemplate.exchange(
            url("/api/disputes"), HttpMethod.GET, jwtEntity(bearerForRole("CLIENT")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(200);
    }

    /** RESTAURATEUR a VIEW:DISPUTES → 200. */
    @Test
    void list_restaurateur_returns200() {
        int status = restTemplate.exchange(
            url("/api/disputes"), HttpMethod.GET, jwtEntity(bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(200);
    }

    /** SUPERADMIN voit tout → 200. */
    @Test
    void list_admin_returns200() {
        int status = restTemplate.exchange(
            url("/api/disputes?status=pending"), HttpMethod.GET, jwtEntity(adminBearer()), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(200);
    }

    // ─── No bearer → 401 ──────────────────────────────────────────────────────────

    @Test
    void list_noBearer_returns401() {
        int status = restTemplate.exchange(
            url("/api/disputes"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(401);
    }

    @Test
    void create_noBearer_returns401() {
        int status = restTemplate.exchange(
            url("/api/reservations/" + UUID.randomUUID() + "/disputes"), HttpMethod.POST,
            jsonJwtEntity(Map.of("reason", "x"), null), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(401);
    }
}
