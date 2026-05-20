package com.onesley.oneclick.core.identity;

import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke RBAC admin ({@code /api/roles}) — page « Gérer les permissions ».
 */
class RoleAdminSmokeIntegrationTest extends AbstractIntegrationTest {

    @Test
    void listRoles_thenMatrix_returns200() {
        ResponseEntity<String> list = restTemplate.exchange(
            url("/api/roles"), HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        assertThat(list.getBody()).contains("SUPERADMIN").contains("permissionCount");

        Matcher m = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(list.getBody());
        assertThat(m.find()).isTrue();
        String roleId = m.group(1);

        ResponseEntity<String> matrix = restTemplate.exchange(
            url("/api/roles/" + roleId + "/permissions"), HttpMethod.GET, jwtEntity(adminBearer()), String.class);
        assertThat(matrix.getStatusCode().value()).isEqualTo(200);
        assertThat(matrix.getBody())
            .contains("\"actions\"").contains("\"menus\"")
            .contains("VIEW").contains("grantedActions");
    }

    @Test
    void listRoles_noJwt_returns401() {
        ResponseEntity<String> r = restTemplate.exchange(
            url("/api/roles"), HttpMethod.GET, jwtEntity(null), String.class);
        assertThat(r.getStatusCode().value()).isEqualTo(401);
    }
}
