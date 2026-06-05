package com.onesley.oneclick.modules.resource_booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L4 « seed » — vérifie que les 3 modules bookables PCC ajoutés par la migration
 * {@code V71__pcc_pools_foot_resources.sql} (Piscine intérieure, Piscine semi-olympique,
 * Foot) sont bien présents dans le parc {@code resources} du tenant palmeraie ET
 * listables via l'endpoint REST {@code GET /api/resource-bookings/resources} sous la
 * RBAC existante (réutilisation de {@code VIEW:RESOURCE_BOOKINGS}, aucun nouveau grant).
 *
 * <p>On passe par la stack HTTP réelle (filter chain OAuth2 → {@code @PreAuthorize} →
 * service → DB) pour prouver que :
 * <ul>
 *   <li>l'admin liste les ressources palmeraie filtrées par {@code resourceType} → 200 ;</li>
 *   <li>les 3 nouveaux types {@code indoor_pool}, {@code olympic_pool}, {@code football_field}
 *       sont présents avec les bons libellés ;</li>
 *   <li>le CLIENT (membre PCC) — qui ne détient QUE {@code VIEW:RESOURCE_BOOKINGS} sur la
 *       gestion du parc — peut lister ces ressources (pas d'escalade requise) → 200 ;</li>
 *   <li>sans JWT → 401.</li>
 * </ul>
 *
 * <p>Lecture seule : le test n'écrit RIEN (aucun teardown). Le seed V71 est idempotent
 * (NOT EXISTS sur UUID déterministes) ; ce test vérifie son effet, pas la création runtime.</p>
 */
class ResourceBookingPccPoolsFootSeedIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    /** UUID du tenant palmeraie (slug stable) — résolu en DB pour ne rien hardcoder côté test. */
    private String palmeraieTenantId() {
        return jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = 'palmeraie' AND deleted_at IS NULL", String.class);
    }

    /** Bearer d'un CLIENT réel (porte VIEW:RESOURCE_BOOKINGS via le graphe role→permissions). */
    private String clientBearer() {
        String id = jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL ORDER BY u.id LIMIT 1", String.class);
        return jwtIssuer.issueAccessToken(UUID.fromString(id), "CLIENT").token();
    }

    /** GET /resources filtré tenant palmeraie + type donné, asserte 200 et renvoie le contenu JSON. */
    private JsonNode listByType(String bearer, String resourceType) throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/resource-bookings/resources?tenantId=" + palmeraieTenantId()
                + "&resourceType=" + resourceType + "&enabledOnly=true&page=0&size=50"),
            HttpMethod.GET, jwtEntity(bearer), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return om.readTree(resp.getBody()).get("content");
    }

    private static boolean containsResource(JsonNode content, String resourceType, String name) {
        for (JsonNode r : content) {
            if (resourceType.equals(r.path("resourceType").asText())
                && name.equals(r.path("name").asText())) {
                return true;
            }
        }
        return false;
    }

    // ─── A. Les 3 nouveaux types sont présents (admin) ───────────────────────────

    @Test
    void v71_seedsThreePccBookableTypes_forPalmeraie() throws Exception {
        String admin = adminBearer();

        JsonNode indoor = listByType(admin, "indoor_pool");
        assertThat(containsResource(indoor, "indoor_pool", "Piscine intérieure"))
            .as("V71 doit seeder la Piscine intérieure (indoor_pool) pour palmeraie").isTrue();

        JsonNode olympic = listByType(admin, "olympic_pool");
        assertThat(containsResource(olympic, "olympic_pool", "Piscine semi-olympique"))
            .as("V71 doit seeder la Piscine semi-olympique (olympic_pool) pour palmeraie").isTrue();

        // Foot : 5 terrains 5v5 (Terrain 1..5).
        JsonNode foot = listByType(admin, "football_field");
        assertThat(foot).as("le parc Foot palmeraie ne doit pas être vide").isNotEmpty();
        assertThat(containsResource(foot, "football_field", "Terrain 1")).isTrue();
        assertThat(containsResource(foot, "football_field", "Terrain 5")).isTrue();
    }

    // ─── B. Le CLIENT (VIEW:RESOURCE_BOOKINGS) peut lister sans escalade ──────────

    @Test
    void client_canListNewPccResources_keepsViewResourceBookings() throws Exception {
        String client = clientBearer();
        // Le membre PCC liste les ressources réservables (GET /resources = VIEW:RESOURCE_BOOKINGS).
        assertThat(containsResource(listByType(client, "indoor_pool"), "indoor_pool", "Piscine intérieure"))
            .isTrue();
        assertThat(containsResource(listByType(client, "olympic_pool"), "olympic_pool", "Piscine semi-olympique"))
            .isTrue();
        assertThat(listByType(client, "football_field")).isNotEmpty();
    }

    // ─── C. Sans JWT → 401 (filter chain OAuth2) ─────────────────────────────────

    @Test
    void noJwt_listResources_returns401() {
        assertThat(restTemplate.exchange(
            url("/api/resource-bookings/resources?resourceType=indoor_pool&page=0&size=5"),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
