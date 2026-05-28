package com.onesley.oneclick.modules.restaurant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L4 « profondeur » — flux end-to-end {@code /api/restaurants} + sous-ressources
 * (staff, services, zones, tables, transfer, invite). Self-clean.
 */
class RestaurantFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private String tenantId() {
        return jdbc.queryForObject("SELECT tenant_id::text FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1", String.class);
    }
    private String userId() {
        return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class);
    }
    private String createRestaurant(String admin, String name) throws Exception {
        ResponseEntity<String> r = restTemplate.exchange(url("/api/restaurants"), HttpMethod.POST,
            jsonJwtEntity(Map.of("tenantId", tenantId(), "name", name, "city", "Casablanca"), admin), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return om.readTree(r.getBody()).get("id").asText();
    }

    @Test
    void restaurant_fullLifecycle_withSubResources() throws Exception {
        String admin = adminBearer();
        String id = createRestaurant(admin, "L4 Resto");

        // GET + list + PATCH + search
        assertThat(restTemplate.exchange(url("/api/restaurants/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/restaurants?city=Casablanca&page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> patch = restTemplate.exchange(url("/api/restaurants/" + id), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("name", "L4 Resto Patched", "status", "active"), admin), String.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(patch.getBody()).get("name").asText()).isEqualTo("L4 Resto Patched");
        assertThat(restTemplate.exchange(url("/api/restaurants/search"), HttpMethod.POST,
            jsonJwtEntity(Map.of("criteria", List.of(), "page", 0, "size", 5), admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // STAFF
        ResponseEntity<String> staffPost = restTemplate.exchange(url("/api/restaurants/" + id + "/staff"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", userId(), "roleCode", "serveur"), admin), String.class);
        assertThat(staffPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String staffId = om.readTree(staffPost.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/restaurants/" + id + "/staff"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/restaurants/staff/" + staffId), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("roleCode", "manager"), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/restaurants/staff/by-user/" + userId()), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/restaurants/staff/" + staffId), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // SERVICES
        ResponseEntity<String> svcPost = restTemplate.exchange(url("/api/restaurants/" + id + "/services"), HttpMethod.POST,
            jsonJwtEntity(Map.of("name", "Déjeuner", "startTime", "12:00:00", "endTime", "15:00:00"), admin), String.class);
        assertThat(svcPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String svcId = om.readTree(svcPost.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/restaurants/" + id + "/services"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/restaurants/services/" + svcId), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("name", "Brunch"), admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/restaurants/services/" + svcId), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // ZONES + TABLES
        ResponseEntity<String> zonePost = restTemplate.exchange(url("/api/restaurants/" + id + "/zones"), HttpMethod.POST,
            jsonJwtEntity(Map.of("name", "Terrasse"), admin), String.class);
        assertThat(zonePost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String zoneId = om.readTree(zonePost.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/restaurants/" + id + "/zones"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> tablePost = restTemplate.exchange(url("/api/restaurants/" + id + "/tables"), HttpMethod.POST,
            jsonJwtEntity(Map.of("zoneId", zoneId, "tableNumber", "T1", "seats", 4), admin), String.class);
        assertThat(tablePost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String tableId = om.readTree(tablePost.getBody()).get("id").asText();
        assertThat(restTemplate.exchange(url("/api/restaurants/" + id + "/tables"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        // PATCH zone (V50 — type/capacité/statut) + round-trip
        ResponseEntity<String> zonePatch = restTemplate.exchange(url("/api/restaurants/zones/" + zoneId), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("type", "terrasse", "capacity", 40, "status", "inactive"), admin), String.class);
        assertThat(zonePatch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(zonePatch.getBody()).get("capacity").asInt()).isEqualTo(40);
        assertThat(om.readTree(zonePatch.getBody()).get("type").asText()).isEqualTo("terrasse");
        // PATCH table (V50 — places/forme/statut) + round-trip
        ResponseEntity<String> tablePatch = restTemplate.exchange(url("/api/restaurants/tables/" + tableId), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("seats", 8, "shape", "ronde", "status", "occupée"), admin), String.class);
        assertThat(tablePatch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(tablePatch.getBody()).get("seats").asInt()).isEqualTo(8);
        assertThat(om.readTree(tablePatch.getBody()).get("shape").asText()).isEqualTo("ronde");
        assertThat(restTemplate.exchange(url("/api/restaurants/tables/" + tableId), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.exchange(url("/api/restaurants/zones/" + zoneId), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // DELETE resto → 404
        assertThat(restTemplate.exchange(url("/api/restaurants/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.exchange(url("/api/restaurants/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void staff_transferBetweenRestaurants() throws Exception {
        String admin = adminBearer();
        String src = createRestaurant(admin, "L4 Src");
        String dst = createRestaurant(admin, "L4 Dst");
        ResponseEntity<String> staffPost = restTemplate.exchange(url("/api/restaurants/" + src + "/staff"), HttpMethod.POST,
            jsonJwtEntity(Map.of("userId", userId(), "roleCode", "serveur"), admin), String.class);
        String staffId = om.readTree(staffPost.getBody()).get("id").asText();
        ResponseEntity<String> transfer = restTemplate.exchange(url("/api/restaurants/staff/transfer"), HttpMethod.POST,
            jsonJwtEntity(Map.of("staffId", staffId, "sourceRestaurantId", src, "targetRestaurantId", dst), admin), String.class);
        assertThat(transfer.getStatusCode()).isEqualTo(HttpStatus.OK);
        // self-clean
        restTemplate.exchange(url("/api/restaurants/" + src), HttpMethod.DELETE, jwtEntity(admin), String.class);
        restTemplate.exchange(url("/api/restaurants/" + dst), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void staff_inviteUnknownEmail_returnsUserExistsFalse() throws Exception {
        String admin = adminBearer();
        String id = createRestaurant(admin, "L4 Invite");
        ResponseEntity<String> invite = restTemplate.exchange(url("/api/restaurants/staff/invite"), HttpMethod.POST,
            jsonJwtEntity(Map.of("restaurantId", id, "email", "inconnu-" + UUID.randomUUID() + "@x.ma", "roleCode", "serveur"), admin), String.class);
        assertThat(invite.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(invite.getBody()).get("userExists").asBoolean()).isFalse();
        restTemplate.exchange(url("/api/restaurants/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void restaurant_unknownId_404() {
        assertThat(restTemplate.exchange(url("/api/restaurants/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createRestaurant_invalidBody_400() {
        assertThat(restTemplate.exchange(url("/api/restaurants"), HttpMethod.POST,
            jsonJwtEntity(Map.of("city", "Casablanca"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRestaurant_asClient_403() {
        assertThat(restTemplate.exchange(url("/api/restaurants"), HttpMethod.POST,
            jsonJwtEntity(Map.of("tenantId", tenantId(), "name", "X", "city", "Casa"), bearerForRole("CLIENT")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
