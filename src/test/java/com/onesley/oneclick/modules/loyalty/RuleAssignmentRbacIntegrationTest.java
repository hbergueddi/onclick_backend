package com.onesley.oneclick.modules.loyalty;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration RBAC + flux E2E de l'assignation en masse d'une tier-rule plateforme
 * aux restaurants (Gap #1) sur la stack réelle (filter chain → @PreAuthorize →
 * service → repo → DB).
 *
 * <p>Contrat : opérations admin-only ({@code VIEW/UPDATE:LOYALTY_TIER}, SUPERADMIN).
 * RESTAURATEUR et CLIENT → 403 (verrouille l'absence de sur-grant).
 *
 * <p>Isolation : restaurant + tier-rule jetables créés/supprimés par test → aucune
 * mutation des données seedées (l'unassign fait un hard delete de la gain_rule créée).
 */
class RuleAssignmentRbacIntegrationTest extends AbstractIntegrationTest {

    private UUID restaurantId;
    private UUID tierRuleId;

    @BeforeEach
    void setup() {
        UUID tenantId = UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM tenants LIMIT 1", String.class));
        restaurantId = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO restaurants (id, tenant_id, name, city) VALUES (?, ?, ?, ?)",
            restaurantId, tenantId, "GAP1-Test-Resto-" + restaurantId, "Casablanca");

        // Tier-rule source créée via l'API admin (taux 25%, min 80, plafond 300).
        @SuppressWarnings("rawtypes")
        ResponseEntity<Map> created = restTemplate.exchange(
            url("/api/loyalty/tier-rules"), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "name", "GAP1-Tier-" + UUID.randomUUID(),
                "type", "premium",
                "conversionRate", 0.25,
                "minTicket", 80,
                "maxPointsPerTicket", 300,
                "enabled", true), adminBearer()),
            Map.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        tierRuleId = UUID.fromString((String) created.getBody().get("id"));
    }

    @AfterEach
    void cleanup() {
        if (restaurantId != null) {
            jdbc.update("DELETE FROM gain_rules WHERE restaurant_id = ?", restaurantId);
        }
        if (tierRuleId != null) {
            jdbc.update("DELETE FROM gain_rules WHERE source_tier_rule_id = ?", tierRuleId);
            jdbc.update("DELETE FROM loyalty_tier_rules WHERE id = ?", tierRuleId);
        }
        if (restaurantId != null) {
            jdbc.update("DELETE FROM restaurants WHERE id = ?", restaurantId);
        }
        restaurantId = null;
        tierRuleId = null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void admin_assign_sync_unassign_happyPath() {
        String admin = adminBearer();

        // ASSIGN → 200, affected=1
        ResponseEntity<Map> assign = restTemplate.exchange(
            url("/api/loyalty/tier-rules/" + tierRuleId + "/assign"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantIds", List.of(restaurantId.toString())), admin), Map.class);
        assertThat(assign.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) assign.getBody().get("affected")).intValue()).isEqualTo(1);

        // La gain_rule du restaurant a bien reçu les champs de la tier-rule (ce que lit snap2earn)
        Map<String, Object> gr = jdbc.queryForMap(
            "SELECT conversion_rate, min_amount, cap_per_visit, source_tier_rule_id "
            + "FROM gain_rules WHERE restaurant_id = ? AND deleted_at IS NULL", restaurantId);
        assertThat((BigDecimal) gr.get("conversion_rate")).isEqualByComparingTo("0.2500");
        assertThat((BigDecimal) gr.get("min_amount")).isEqualByComparingTo("80.00");
        assertThat(((Number) gr.get("cap_per_visit")).intValue()).isEqualTo(300);
        assertThat(gr.get("source_tier_rule_id").toString()).isEqualTo(tierRuleId.toString());

        // GET assignments contient le restaurant
        ResponseEntity<Map[]> list = restTemplate.exchange(
            url("/api/loyalty/tier-rules/" + tierRuleId + "/assignments"), HttpMethod.GET,
            jwtEntity(admin), Map[].class);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        assertThat(Arrays.stream(list.getBody())
            .anyMatch(m -> restaurantId.toString().equals(m.get("restaurantId")))).isTrue();

        // GET counts contient la tier-rule (=1)
        ResponseEntity<Map> counts = restTemplate.exchange(
            url("/api/loyalty/tier-rules/assignments/counts"), HttpMethod.GET,
            jwtEntity(admin), Map.class);
        assertThat(counts.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) counts.getBody().get(tierRuleId.toString())).intValue()).isEqualTo(1);

        // SYNC → 200, affected=1
        ResponseEntity<Map> sync = restTemplate.exchange(
            url("/api/loyalty/tier-rules/" + tierRuleId + "/sync"), HttpMethod.POST,
            jwtEntity(admin), Map.class);
        assertThat(sync.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) sync.getBody().get("affected")).intValue()).isEqualTo(1);

        // UNASSIGN → 200, affected=1
        ResponseEntity<Map> unassign = restTemplate.exchange(
            url("/api/loyalty/tier-rules/" + tierRuleId + "/unassign"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantIds", List.of(restaurantId.toString())), admin), Map.class);
        assertThat(unassign.getStatusCode().value()).isEqualTo(200);
        assertThat(((Number) unassign.getBody().get("affected")).intValue()).isEqualTo(1);

        // gain_rule supprimée → snap2earn retombe sur le défaut
        Integer remaining = jdbc.queryForObject(
            "SELECT COUNT(*) FROM gain_rules WHERE restaurant_id = ? AND deleted_at IS NULL",
            Integer.class, restaurantId);
        assertThat(remaining).isZero();

        // GET assignments vide
        ResponseEntity<Map[]> after = restTemplate.exchange(
            url("/api/loyalty/tier-rules/" + tierRuleId + "/assignments"), HttpMethod.GET,
            jwtEntity(admin), Map[].class);
        assertThat(after.getBody()).isEmpty();
    }

    @Test
    void restaurateur_assign_returns403() {
        int status = restTemplate.exchange(
            url("/api/loyalty/tier-rules/" + tierRuleId + "/assign"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantIds", List.of(restaurantId.toString())),
                bearerForRole("RESTAURATEUR")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    @Test
    void client_listAssignments_returns403() {
        int status = restTemplate.exchange(
            url("/api/loyalty/tier-rules/" + tierRuleId + "/assignments"), HttpMethod.GET,
            jwtEntity(bearerForRole("CLIENT")), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }
}
