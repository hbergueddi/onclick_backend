package com.onesley.oneclick.modules.family;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC/ABAC L4 — « Ma Famille » (PCC Lot 5, ressource {@code FAMILY} seedée par V68).
 *
 * <p>Invariants vérifiés :
 * <ul>
 *   <li>Sans JWT → 401 (GET / POST / DELETE).</li>
 *   <li>CLIENT (VIEW/CREATE/DELETE:FAMILY) : flow complet add → list → points-history → remove.</li>
 *   <li>add d'un membre du même tenant par email/code/téléphone → 201.</li>
 *   <li>add de soi-même → 400 ; identifiant inconnu → 404.</li>
 *   <li>limite 10 → 422.</li>
 *   <li>points-history d'un membre NON ajouté → 403 (relation-check ABAC).</li>
 *   <li>retrait par B (le proche) autorisé ; retrait par un tiers → 403.</li>
 * </ul>
 *
 * <p>Données : les membres PCC seedés ({@code memberN@palmeraie.com}, rôle CLIENT, tenant
 * palmeraie). Le test nettoie les liens famille qu'il crée en teardown (pas de soft delete).</p>
 */
class PccFamilyRbacIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();
    private final java.util.List<UUID> createdRelationIds = new java.util.ArrayList<>();

    private UUID userIdByEmail(String email) {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM users WHERE email = ?", String.class, email));
    }

    /** Bearer pour un membre PCC précis (rôle CLIENT). */
    private String bearerFor(String email) {
        return jwtIssuer.issueAccessToken(userIdByEmail(email), "CLIENT").token();
    }

    /** Insère un lien direct (A→B) en base et le track pour cleanup. */
    private UUID seedLink(String memberEmail, String relatedEmail, String relation) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO pcc_family_members (id, member_id, related_member_id, relation, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, now(), now()) ON CONFLICT (member_id, related_member_id) DO NOTHING",
            id, userIdByEmail(memberEmail), userIdByEmail(relatedEmail), relation);
        // Récupère l'id réel (en cas de conflit la ligne préexistante garde son id).
        UUID realId = UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM pcc_family_members WHERE member_id = ? AND related_member_id = ?",
            String.class, userIdByEmail(memberEmail), userIdByEmail(relatedEmail)));
        createdRelationIds.add(realId);
        return realId;
    }

    @AfterEach
    void cleanup() {
        for (UUID id : createdRelationIds) {
            jdbc.update("DELETE FROM pcc_family_members WHERE id = ?", id);
        }
        createdRelationIds.clear();
        // Filet : retire d'éventuels liens créés via l'API par les tests (member4..member9 cibles).
        jdbc.update("DELETE FROM pcc_family_members WHERE member_id = ? AND related_member_id IN ("
            + "SELECT id FROM users WHERE email IN ('member4@palmeraie.com','member5@palmeraie.com'))",
            userIdByEmail("member1@palmeraie.com"));
    }

    // ─── A. 401 sans JWT ─────────────────────────────────────────────────────────

    @Test
    void noJwt_returns401() {
        assertThat(restTemplate.exchange(url("/api/pcc/family"),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange(url("/api/pcc/family"),
            HttpMethod.POST, jsonJwtEntity("{\"identifier\":\"x@x.ma\"}", null), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange(url("/api/pcc/family/" + UUID.randomUUID()),
            HttpMethod.DELETE, jwtEntity(null), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── B. CLIENT — flow add → list → history → remove ──────────────────────────

    @Test
    void client_add_list_history_remove_flow() throws Exception {
        String caller = bearerFor("member1@palmeraie.com");

        // add member4 par email → 201, status "added".
        ResponseEntity<String> addResp = restTemplate.exchange(url("/api/pcc/family"),
            HttpMethod.POST,
            jsonJwtEntity("{\"identifier\":\"member4@palmeraie.com\",\"relation\":\"Frère·Sœur\"}", caller),
            String.class);
        assertThat(addResp.getStatusCode())
            .as("add member4 — reçu %s, body=%s", addResp.getStatusCode(), addResp.getBody())
            .isEqualTo(HttpStatus.CREATED);
        JsonNode added = om.readTree(addResp.getBody());
        assertThat(added.get("status").asText()).isEqualTo("added");
        UUID relationId = UUID.fromString(added.get("relationId").asText());
        createdRelationIds.add(relationId);

        // idempotent : re-add même proche → 201 "already_added", même relationId.
        ResponseEntity<String> addAgain = restTemplate.exchange(url("/api/pcc/family"),
            HttpMethod.POST,
            jsonJwtEntity("{\"identifier\":\"member4@palmeraie.com\"}", caller), String.class);
        assertThat(addAgain.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(om.readTree(addAgain.getBody()).get("status").asText()).isEqualTo("already_added");
        assertThat(om.readTree(addAgain.getBody()).get("relationId").asText()).isEqualTo(relationId.toString());

        // list → contient member4.
        ResponseEntity<String> listResp = restTemplate.exchange(url("/api/pcc/family"),
            HttpMethod.GET, jwtEntity(caller), String.class);
        assertThat(listResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode arr = om.readTree(listResp.getBody());
        assertThat(arr.isArray()).isTrue();
        UUID member4Id = userIdByEmail("member4@palmeraie.com");
        boolean present = false;
        for (JsonNode n : arr) {
            if (member4Id.toString().equals(n.get("memberId").asText())) {
                present = true;
                assertThat(n.has("totalRemainingPoints")).isTrue();
                assertThat(n.has("firstName")).isTrue();
            }
        }
        assertThat(present).as("member4 présent dans la liste famille").isTrue();

        // points-history du proche ajouté → 200 (relation existe).
        ResponseEntity<String> histResp = restTemplate.exchange(
            url("/api/pcc/family/" + member4Id + "/points-history"),
            HttpMethod.GET, jwtEntity(caller), String.class);
        assertThat(histResp.getStatusCode())
            .as("history member4 — reçu %s, body=%s", histResp.getStatusCode(), histResp.getBody())
            .isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(histResp.getBody()).isArray()).isTrue();

        // remove (par A) → 204, puis absent de la liste.
        ResponseEntity<String> delResp = restTemplate.exchange(url("/api/pcc/family/" + relationId),
            HttpMethod.DELETE, jwtEntity(caller), String.class);
        assertThat(delResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pcc_family_members WHERE id = ?", Integer.class, relationId);
        assertThat(cnt).isZero();
    }

    // ─── C. add self → 400 ; identifiant inconnu → 404 ───────────────────────────

    @Test
    void add_self_returns400() {
        ResponseEntity<String> resp = restTemplate.exchange(url("/api/pcc/family"),
            HttpMethod.POST,
            jsonJwtEntity("{\"identifier\":\"member1@palmeraie.com\"}", bearerFor("member1@palmeraie.com")),
            String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void add_unknownIdentifier_returns404() {
        ResponseEntity<String> resp = restTemplate.exchange(url("/api/pcc/family"),
            HttpMethod.POST,
            jsonJwtEntity("{\"identifier\":\"ghost-nobody@nowhere.invalid\"}", bearerFor("member1@palmeraie.com")),
            String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─── D. points-history sans relation → 403 ───────────────────────────────────

    @Test
    void history_withoutRelation_returns403() {
        // member1 n'a PAS ajouté member9 → 403 (relation-check ABAC).
        UUID member9 = userIdByEmail("member9@palmeraie.com");
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/pcc/family/" + member9 + "/points-history"),
            HttpMethod.GET, jwtEntity(bearerFor("member1@palmeraie.com")), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── E. limite 10 → 422 ───────────────────────────────────────────────────────

    @Test
    void add_overLimit_returns422() {
        String caller = bearerFor("member2@palmeraie.com");
        // Seed 10 liens (member2 → member3..member12) pour saturer la limite.
        for (int i = 3; i <= 12; i++) {
            seedLink("member2@palmeraie.com", "member" + i + "@palmeraie.com", "Autre");
        }
        // Le 11e (member13) → 422.
        ResponseEntity<String> resp = restTemplate.exchange(url("/api/pcc/family"),
            HttpMethod.POST,
            jsonJwtEntity("{\"identifier\":\"member13@palmeraie.com\"}", caller), String.class);
        assertThat(resp.getStatusCode().value())
            .as("11e ajout — reçu %s, body=%s", resp.getStatusCode(), resp.getBody())
            .isEqualTo(422);
    }

    // ─── F. remove par B (le proche) autorisé ; par un tiers → 403 ───────────────

    @Test
    void remove_byRelatedB_succeeds_byStranger_forbidden() {
        // member1 ajoute member5 ; member5 (= B) peut retirer le lien.
        UUID relationId = seedLink("member1@palmeraie.com", "member5@palmeraie.com", "Enfant");

        // Un tiers (member9) ne peut pas retirer ce lien → 403.
        ResponseEntity<String> byStranger = restTemplate.exchange(url("/api/pcc/family/" + relationId),
            HttpMethod.DELETE, jwtEntity(bearerFor("member9@palmeraie.com")), String.class);
        assertThat(byStranger.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // B (member5) retire son propre lien → 204.
        ResponseEntity<String> byB = restTemplate.exchange(url("/api/pcc/family/" + relationId),
            HttpMethod.DELETE, jwtEntity(bearerFor("member5@palmeraie.com")), String.class);
        assertThat(byB.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pcc_family_members WHERE id = ?", Integer.class, relationId);
        assertThat(cnt).isZero();
    }
}
