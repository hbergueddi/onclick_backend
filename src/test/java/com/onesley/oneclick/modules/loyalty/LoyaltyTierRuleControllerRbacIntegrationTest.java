package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC + CRUD de la ressource {@code LOYALTY_TIER} (paliers de
 * fidélité PLATEFORME, migration V43) sur la stack de sécurité réelle
 * (filter chain → JwtDecoder → UserRoleAuthoritiesConverter → @PreAuthorize →
 * service → repo → DB).
 *
 * <p>Contrat (après migration V99) : la MODIFICATION (CREATE/UPDATE/DELETE) reste
 * admin-only (SUPERADMIN). La LECTURE (VIEW) est accordée en plus au RESTAURATEUR et
 * au GROUP_ADMIN (bloc « Paliers globaux Lounge » en ProDesk) → GET = 200, mais
 * POST/PATCH/DELETE = 403 (pas de sur-grant). CLIENT n'a aucun grant LOYALTY_TIER →
 * 403 même sur GET.
 */
class LoyaltyTierRuleControllerRbacIntegrationTest extends AbstractIntegrationTest {

    private UUID createdId;

    @AfterEach
    void cleanup() {
        if (createdId != null) {
            jdbc.update("DELETE FROM loyalty_tier_rules WHERE id = ?", createdId);
            createdId = null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void superadmin_fullCrud_happyPath() {
        String admin = adminBearer();

        // CREATE → 201
        Map<String, Object> body = Map.of(
            "name", "Test-Tier-" + UUID.randomUUID(),
            "type", "premium",
            "conversionRate", 0.15,
            "minTicket", 100,
            "maxPointsPerTicket", 300,
            "enabled", true);
        ResponseEntity<Map> created = restTemplate.exchange(
            url("/api/loyalty/tier-rules"), HttpMethod.POST, jsonJwtEntity(body, admin), Map.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody()).isNotNull();
        createdId = UUID.fromString((String) created.getBody().get("id"));
        assertThat(created.getBody().get("type")).isEqualTo("premium");

        // LIST (admin) contient la création
        ResponseEntity<Map[]> list = restTemplate.exchange(
            url("/api/loyalty/tier-rules"), HttpMethod.GET, jwtEntity(admin), Map[].class);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        assertThat(Arrays.stream(list.getBody())
            .anyMatch(m -> createdId.toString().equals(m.get("id")))).isTrue();

        // PATCH enabled=false → 200
        int patch = restTemplate.exchange(
            url("/api/loyalty/tier-rules/" + createdId), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("enabled", false), admin), String.class).getStatusCode().value();
        assertThat(patch).isEqualTo(200);

        // DELETE (soft) → 204
        int del = restTemplate.exchange(
            url("/api/loyalty/tier-rules/" + createdId), HttpMethod.DELETE,
            jwtEntity(admin), String.class).getStatusCode().value();
        assertThat(del).isEqualTo(204);
    }

    @Test
    void restaurateur_list_returns200_readGranted() {
        // V99 : VIEW:LOYALTY_TIER accordé au RESTAURATEUR (lecture seule).
        int status = restTemplate.exchange(
            url("/api/loyalty/tier-rules"), HttpMethod.GET,
            jwtEntity(bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(200);
    }

    @Test
    void groupAdmin_list_returns200_readGranted() {
        // V99 : VIEW:LOYALTY_TIER accordé aussi au GROUP_ADMIN (lecture seule).
        int status = restTemplate.exchange(
            url("/api/loyalty/tier-rules"), HttpMethod.GET,
            jwtEntity(bearerForRole("GROUP_ADMIN")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(200);
    }

    @Test
    void restaurateur_create_returns403_notOvergranted() {
        // VIEW seulement : pas de CREATE:LOYALTY_TIER → POST reste interdit.
        int status = restTemplate.exchange(
            url("/api/loyalty/tier-rules"), HttpMethod.POST,
            jsonJwtEntity(Map.of("name", "x"), bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void restaurateur_patch_returns403_notOvergranted() {
        // VIEW seulement : pas de UPDATE:LOYALTY_TIER → PATCH reste interdit.
        int status = restTemplate.exchange(
            url("/api/loyalty/tier-rules/" + UUID.randomUUID()), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("enabled", false), bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void restaurateur_delete_returns403_notOvergranted() {
        // VIEW seulement : pas de DELETE:LOYALTY_TIER → DELETE reste interdit.
        int status = restTemplate.exchange(
            url("/api/loyalty/tier-rules/" + UUID.randomUUID()), HttpMethod.DELETE,
            jwtEntity(bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void client_list_returns403_noGrant() {
        // CLIENT n'a aucun grant LOYALTY_TIER → 403 même sur GET.
        int status = restTemplate.exchange(
            url("/api/loyalty/tier-rules"), HttpMethod.GET,
            jwtEntity(bearerForRole("CLIENT")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }
}
