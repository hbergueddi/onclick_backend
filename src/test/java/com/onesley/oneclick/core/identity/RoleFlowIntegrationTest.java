package com.onesley.oneclick.core.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L4 « profondeur » — {@code /api/roles} : list + matrice permissions + PUT (round-trip
 * NON destructif : on relit la grille courante et on la ré-applique à l'identique).
 */
class RoleFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private String roleId() { return jdbc.queryForObject("SELECT id::text FROM roles WHERE code='CLIENT' LIMIT 1", String.class); }

    /** Aplatit récursivement l'arbre de menus en [{menuId, actions}] (préserve la grille). */
    private void flatten(JsonNode menus, List<Map<String, Object>> out) {
        for (JsonNode m : menus) {
            List<String> actions = new ArrayList<>();
            m.get("grantedActions").forEach(a -> actions.add(a.asText()));
            out.add(Map.of("menuId", m.get("id").asText(), "actions", actions));
            if (m.has("children")) flatten(m.get("children"), out);
        }
    }

    @Test
    void roles_list_getPermissions_putRoundTrip() throws Exception {
        String admin = adminBearer();
        String rid = roleId();

        assertThat(restTemplate.exchange(url("/api/roles"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> get = restTemplate.exchange(url("/api/roles/" + rid + "/permissions"),
            HttpMethod.GET, jwtEntity(admin), String.class);
        assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode matrix = om.readTree(get.getBody());

        List<Map<String, Object>> menus = new ArrayList<>();
        flatten(matrix.get("menus"), menus);
        ResponseEntity<String> put = restTemplate.exchange(url("/api/roles/" + rid + "/permissions"),
            HttpMethod.PUT, jsonJwtEntity(Map.of("menus", menus), admin), String.class);
        assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void putPermissions_unknownRole_404() {
        assertThat(restTemplate.exchange(url("/api/roles/" + UUID.randomUUID() + "/permissions"),
            HttpMethod.PUT, jsonJwtEntity(Map.of("menus", List.of()), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void roles_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/roles"), HttpMethod.GET, jwtEntity(null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
