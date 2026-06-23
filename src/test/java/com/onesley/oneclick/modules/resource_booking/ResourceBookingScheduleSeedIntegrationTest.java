package com.onesley.oneclick.modules.resource_booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que la migration {@code V96__resources_schedule_columns.sql} expose, via la stack HTTP
 * réelle ({@code GET /api/resource-bookings/resources}), les champs de planning nécessaires à la
 * grille de créneaux du client : {@code openingHours}, {@code slotDurationMinutes}, {@code maxInvitees}.
 *
 * <p>On s'appuie sur les ressources Foot seedées de façon déterministe par {@code V71} (Terrain 1..5,
 * {@code football_field}) que V96 renseigne (60 min · 9 invités · horaires lun-dim). Cible reproductible
 * (aucune dépendance à un seed externe) — contrairement au padel, dont les rangées sont seedées hors
 * Flyway dans {@code oneclick_enterprise}.
 *
 * <p>RBAC inchangée : {@code VIEW:RESOURCE_BOOKINGS} via {@code hasAuthority} (admin 200, sans JWT 401).
 * Lecture seule (aucun teardown).
 */
class ResourceBookingScheduleSeedIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private String palmeraieTenantId() {
        return jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = 'palmeraie' AND deleted_at IS NULL", String.class);
    }

    private JsonNode listByType(String bearer, String resourceType) throws Exception {
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/resource-bookings/resources?tenantId=" + palmeraieTenantId()
                + "&resourceType=" + resourceType + "&enabledOnly=true&page=0&size=50"),
            HttpMethod.GET, jwtEntity(bearer), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return om.readTree(resp.getBody()).get("content");
    }

    // ─── A. V96 renseigne le planning des ressources Foot (seed déterministe V71) ────────────────

    @Test
    void v96_exposesScheduleFields_forFootballField() throws Exception {
        JsonNode foot = listByType(adminBearer(), "football_field");
        assertThat(foot).as("le parc Foot palmeraie ne doit pas être vide (V71)").isNotEmpty();

        JsonNode r = foot.get(0);
        assertThat(r.path("slotDurationMinutes").asInt())
            .as("V96 doit renseigner slot_duration_minutes=60 pour le foot").isEqualTo(60);
        assertThat(r.path("maxInvitees").asInt())
            .as("V96 doit renseigner max_invitees=9 (5v5) pour le foot").isEqualTo(9);

        JsonNode hours = r.path("openingHours");
        assertThat(hours.isObject()).as("openingHours doit être un objet JSON {jour:[plages]}").isTrue();
        assertThat(hours.has("mon")).as("openingHours doit porter au moins lundi").isTrue();
        assertThat(hours.path("mon").get(0).asText())
            .as("plage lundi foot = 09:00-22:00").isEqualTo("09:00-22:00");
        assertThat(hours.path("sun").get(0).asText())
            .as("plage dimanche foot = 09:00-20:00").isEqualTo("09:00-20:00");
    }

    // ─── B. Sans JWT → 401 (filter chain OAuth2, hasAuthority préservé) ──────────────────────────

    @Test
    void noJwt_listResources_returns401() {
        assertThat(restTemplate.exchange(
            url("/api/resource-bookings/resources?resourceType=football_field&page=0&size=5"),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
