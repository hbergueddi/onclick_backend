package com.onesley.oneclick.core.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H (V79) — type de membre PCC ({@code users.pcc_member_type}) via
 * {@code PATCH /api/users/{id}/pcc-member-type} (UPDATE:USERS, admin).
 *
 * <p>Stack réelle (HTTP + JWT HS256 + filter chain → controller → service → repo →
 * {@code oneclick_enterprise}). Vérifie : admin pose le type → 200 + exposé par
 * {@code GET /api/users/{id}} (UserDto.pccMemberType) ; un CLIENT (sans UPDATE:USERS)
 * ne peut PAS s'auto-attribuer une remise → 403 ; valeur invalide → 400 ; anonyme → 401 ;
 * {@code null} retire le statut.
 *
 * <p>Calque {@link UserAllergensFlowIntegrationTest}.
 */
class UserPccMemberTypeFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    /** User CLIENT jetable (évite de muter les données seed). */
    private String createClient(String admin) throws Exception {
        String roleId = jdbc.queryForObject("SELECT id::text FROM roles WHERE code='CLIENT' LIMIT 1", String.class);
        var r = restTemplate.exchange(url("/api/users"), HttpMethod.POST, jsonJwtEntity(Map.of(
            "roleId", roleId, "email", "pccmt-it-" + UUID.randomUUID() + "@x.ma",
            "password", "password1234", "firstName", "PccMt", "lastName", "Test"), admin), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("création user jetable").isTrue();
        return om.readTree(r.getBody()).get("id").asText();
    }

    private String pccMemberTypeOf(String adminBearer, String userId) throws Exception {
        ResponseEntity<String> r = restTemplate.exchange(
            url("/api/users/" + userId), HttpMethod.GET, jwtEntity(adminBearer), String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        var node = om.readTree(r.getBody()).get("pccMemberType");
        return node == null || node.isNull() ? null : node.asText();
    }

    private int patch(String userId, Object body, String bearer) {
        return restTemplate.exchange(url("/api/users/" + userId + "/pcc-member-type"),
            HttpMethod.PATCH, jsonJwtEntity(body, bearer), String.class).getStatusCode().value();
    }

    @Test
    void admin_setsMemberType_persisted_andClient_cannotSelfAssign() throws Exception {
        String admin = adminBearer();
        String userId = createClient(admin);
        String clientBearer = jwtIssuer.issueAccessToken(UUID.fromString(userId), "CLIENT").token();
        try {
            // Au départ : pas de type.
            assertThat(pccMemberTypeOf(admin, userId)).isNull();

            // Admin pose "resident" → 200 + persisté.
            assertThat(patch(userId, Map.of("memberType", "resident"), admin)).isEqualTo(200);
            assertThat(pccMemberTypeOf(admin, userId)).isEqualTo("resident");

            // RBAC : le CLIENT lui-même n'a pas UPDATE:USERS → 403 (pas d'auto-attribution de remise).
            assertThat(patch(userId, Map.of("memberType", "resident"), clientBearer)).isEqualTo(403);

            // Valeur hors vocabulaire → 400 (Bean Validation @Pattern).
            assertThat(patch(userId, Map.of("memberType", "vip"), admin)).isEqualTo(400);

            // Anonyme → 401.
            int anon = restTemplate.exchange(url("/api/users/" + userId + "/pcc-member-type"),
                HttpMethod.PATCH, null, String.class).getStatusCode().value();
            assertThat(anon).isEqualTo(401);

            // null retire le statut → 200 + null.
            Map<String, Object> clear = new HashMap<>();
            clear.put("memberType", null);
            assertThat(patch(userId, clear, admin)).isEqualTo(200);
            assertThat(pccMemberTypeOf(admin, userId)).isNull();
        } finally {
            restTemplate.exchange(url("/api/users/" + userId), HttpMethod.DELETE, jwtEntity(admin), String.class);
        }
    }
}
