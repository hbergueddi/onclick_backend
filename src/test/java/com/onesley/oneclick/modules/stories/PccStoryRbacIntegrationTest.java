package com.onesley.oneclick.modules.stories;

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
 * RBAC/ABAC L4 — « Stories » (PCC, ressource {@code STORIES} seedée par V72).
 *
 * <p>Invariants vérifiés sur la stack réelle (filter chain → JwtDecoder → converter →
 * {@code @PreAuthorize} → service ABAC → DB) :
 * <ul>
 *   <li>Sans JWT → 401.</li>
 *   <li>CLIENT (membre PCC) → 200 sur GET (a VIEW:STORIES — c'est du contenu destiné au membre)
 *       mais 403 sur POST/PATCH/DELETE (pas d'écriture).</li>
 *   <li>staff/owner PCC (RESTAURATEUR scopé palmeraie) : flow create → list → update → delete.</li>
 *   <li>tenant-scope : un RESTAURATEUR d'un AUTRE tenant (HOMU) → 403 sur PATCH/DELETE d'une story
 *       palmeraie (a UPDATE/DELETE:STORIES au RBAC, mais ABAC service : pas staff du tenant de la
 *       story).</li>
 *   <li>visibilité membre : une story programmée (publishAt futur) n'apparaît PAS dans le GET d'un
 *       membre, mais apparaît pour le staff (vue gestion).</li>
 *   <li>soft-delete par l'admin global → 204, story sort du feed.</li>
 * </ul>
 *
 * <p>Données : owners PCC seedés ({@code owner@*pcc.com}, RESTAURATEUR, tenant palmeraie, staff des
 * restos palmeraie) + membres ({@code memberN@palmeraie.com}, CLIENT). Les stories créées par le
 * test sont supprimées (hard) en teardown.</p>
 */
class PccStoryRbacIntegrationTest extends AbstractIntegrationTest {

    private static final String PALMERAIE_OWNER = "owner@padelpcc.com"; // RESTAURATEUR + tenant palmeraie
    private static final String PALMERAIE_MEMBER = "member1@palmeraie.com"; // CLIENT palmeraie
    private static final String HOMU_OWNER = "directeur@homu.com"; // RESTAURATEUR d'un AUTRE tenant

    private final ObjectMapper om = new ObjectMapper();
    private final java.util.List<UUID> createdIds = new java.util.ArrayList<>();

    private UUID userIdByEmail(String email) {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM users WHERE email = ?", String.class, email));
    }

    /** Bearer pour un user précis avec son rôle réel (lookup DB). */
    private String bearerFor(String email) {
        String roleCode = jdbc.queryForObject(
            "SELECT r.code FROM users u JOIN roles r ON r.id = u.role_id WHERE u.email = ?",
            String.class, email);
        return jwtIssuer.issueAccessToken(userIdByEmail(email), roleCode).token();
    }

    @AfterEach
    void cleanup() {
        for (UUID id : createdIds) {
            jdbc.update("DELETE FROM pcc_stories WHERE id = ?", id);
        }
        createdIds.clear();
    }

    // ─── A. 401 sans JWT ─────────────────────────────────────────────────────────

    @Test
    void noJwt_returns401() {
        assertThat(restTemplate.exchange(url("/api/pcc/stories"),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange(url("/api/pcc/stories"),
            HttpMethod.POST, jsonJwtEntity("{\"mediaUrl\":\"https://cdn/x.jpg\"}", null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── B. CLIENT : VIEW autorisé, écriture interdite ───────────────────────────

    @Test
    void client_canView_butCannotWrite() {
        String member = bearerFor(PALMERAIE_MEMBER);
        // GET autorisé (200) — une story est du contenu destiné au membre.
        assertThat(restTemplate.exchange(url("/api/pcc/stories"),
            HttpMethod.GET, jwtEntity(member), String.class).getStatusCode())
            .isEqualTo(HttpStatus.OK);
        // POST interdit (403) — pas de CREATE:STORIES.
        assertThat(restTemplate.exchange(url("/api/pcc/stories"),
            HttpMethod.POST, jsonJwtEntity("{\"mediaUrl\":\"https://cdn/x.jpg\"}", member), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // DELETE interdit (403).
        assertThat(restTemplate.exchange(url("/api/pcc/stories/" + UUID.randomUUID()),
            HttpMethod.DELETE, jwtEntity(member), String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── C. staff flow : create → list → update → delete ─────────────────────────

    @Test
    void staff_create_list_update_delete_flow() throws Exception {
        String owner = bearerFor(PALMERAIE_OWNER);
        UUID ownerId = userIdByEmail(PALMERAIE_OWNER);

        // 1. create (image, publiée, durée 15) → 201.
        ResponseEntity<String> createResp = restTemplate.exchange(url("/api/pcc/stories"),
            HttpMethod.POST,
            jsonJwtEntity("{\"mediaUrl\":\"https://cdn/padel.jpg\",\"mediaType\":\"image\",\"caption\":\"Tournoi Padel\",\"durationS\":15,\"sortOrder\":1}", owner),
            String.class);
        assertThat(createResp.getStatusCode())
            .as("create — reçu %s, body=%s", createResp.getStatusCode(), createResp.getBody())
            .isEqualTo(HttpStatus.CREATED);
        JsonNode created = om.readTree(createResp.getBody());
        UUID id = UUID.fromString(created.get("id").asText());
        createdIds.add(id);
        assertThat(created.get("authorId").asText()).isEqualTo(ownerId.toString());
        assertThat(created.get("mediaType").asText()).isEqualTo("image");
        assertThat(created.get("caption").asText()).isEqualTo("Tournoi Padel");
        assertThat(created.get("visible").asBoolean()).isTrue();

        // 2. GET / (staff → voit tout) contient la story.
        ResponseEntity<String> list = restTemplate.exchange(url("/api/pcc/stories"),
            HttpMethod.GET, jwtEntity(owner), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains(id.toString());

        // 3. PATCH (édition : type vidéo + légende) → 200.
        ResponseEntity<String> editResp = restTemplate.exchange(url("/api/pcc/stories/" + id),
            HttpMethod.PATCH,
            jsonJwtEntity("{\"mediaUrl\":\"https://cdn/padel.mp4\",\"mediaType\":\"video\",\"caption\":\"Finale Padel\",\"durationS\":30,\"sortOrder\":1}", owner),
            String.class);
        assertThat(editResp.getStatusCode())
            .as("edit — reçu %s, body=%s", editResp.getStatusCode(), editResp.getBody())
            .isEqualTo(HttpStatus.OK);
        JsonNode edited = om.readTree(editResp.getBody());
        assertThat(edited.get("mediaType").asText()).isEqualTo("video");
        assertThat(edited.get("caption").asText()).isEqualTo("Finale Padel");
        assertThat(edited.get("durationS").asInt()).isEqualTo(30);

        // 4. DELETE → 204, story sort du feed (soft-delete).
        assertThat(restTemplate.exchange(url("/api/pcc/stories/" + id),
            HttpMethod.DELETE, jwtEntity(owner), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        Integer alive = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pcc_stories WHERE id = ? AND deleted_at IS NULL",
            Integer.class, id);
        assertThat(alive).isEqualTo(0);
    }

    // ─── D. tenant-scope : un owner d'un AUTRE tenant ne peut PATCH/DELETE ────────

    @Test
    void crossTenantOwner_cannotEditOrDelete_palmeraieStory() throws Exception {
        String palmeraieOwner = bearerFor(PALMERAIE_OWNER);
        String homuOwner = bearerFor(HOMU_OWNER); // RESTAURATEUR d'un AUTRE tenant

        // Story palmeraie créée par le owner palmeraie.
        ResponseEntity<String> createResp = restTemplate.exchange(url("/api/pcc/stories"),
            HttpMethod.POST,
            jsonJwtEntity("{\"mediaUrl\":\"https://cdn/interne.jpg\",\"caption\":\"Interne PCC\"}", palmeraieOwner),
            String.class);
        UUID id = UUID.fromString(om.readTree(createResp.getBody()).get("id").asText());
        createdIds.add(id);

        // L'owner HOMU a UPDATE/DELETE:STORIES (RBAC) mais PAS sur le tenant palmeraie (ABAC).
        assertThat(restTemplate.exchange(url("/api/pcc/stories/" + id),
            HttpMethod.PATCH,
            jsonJwtEntity("{\"mediaUrl\":\"https://cdn/pirate.jpg\"}", homuOwner),
            String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(restTemplate.exchange(url("/api/pcc/stories/" + id),
            HttpMethod.DELETE, jwtEntity(homuOwner), String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        // La story existe toujours (non supprimée).
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pcc_stories WHERE id = ? AND deleted_at IS NULL",
            Integer.class, id);
        assertThat(cnt).isEqualTo(1);
    }

    // ─── E. visibilité membre : programmée invisible au membre, visible au staff ──

    @Test
    void scheduledStory_hiddenFromMember_visibleToStaff() throws Exception {
        String owner = bearerFor(PALMERAIE_OWNER);
        String member = bearerFor(PALMERAIE_MEMBER);

        // Story programmée dans le futur (publishAt = now + 2 jours).
        String futureIso = java.time.Instant.now().plus(2, java.time.temporal.ChronoUnit.DAYS).toString();
        ResponseEntity<String> createResp = restTemplate.exchange(url("/api/pcc/stories"),
            HttpMethod.POST,
            jsonJwtEntity("{\"mediaUrl\":\"https://cdn/soon.jpg\",\"caption\":\"Bientôt\",\"publishAt\":\"" + futureIso + "\"}", owner),
            String.class);
        UUID id = UUID.fromString(om.readTree(createResp.getBody()).get("id").asText());
        createdIds.add(id);

        // Le membre NE voit PAS la story programmée (filtre publish_at <= now).
        ResponseEntity<String> memberList = restTemplate.exchange(url("/api/pcc/stories"),
            HttpMethod.GET, jwtEntity(member), String.class);
        assertThat(memberList.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(memberList.getBody())
            .as("une story programmée ne doit pas apparaître au membre")
            .doesNotContain(id.toString());

        // Le staff la voit (vue gestion → toutes, incl. programmées).
        ResponseEntity<String> staffList = restTemplate.exchange(url("/api/pcc/stories"),
            HttpMethod.GET, jwtEntity(owner), String.class);
        assertThat(staffList.getBody()).contains(id.toString());
    }

    // ─── F. admin global : soft-delete → 204 ─────────────────────────────────────

    @Test
    void admin_softDelete() throws Exception {
        String owner = bearerFor(PALMERAIE_OWNER);
        ResponseEntity<String> createResp = restTemplate.exchange(url("/api/pcc/stories"),
            HttpMethod.POST,
            jsonJwtEntity("{\"mediaUrl\":\"https://cdn/del.jpg\",\"caption\":\"À supprimer\"}", owner),
            String.class);
        UUID id = UUID.fromString(om.readTree(createResp.getBody()).get("id").asText());
        createdIds.add(id);

        // soft-delete par l'admin global (SUPERADMIN → bypass staff tenant) → 204.
        assertThat(restTemplate.exchange(url("/api/pcc/stories/" + id),
            HttpMethod.DELETE, jwtEntity(adminBearer()), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        Integer alive = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pcc_stories WHERE id = ? AND deleted_at IS NULL",
            Integer.class, id);
        assertThat(alive).isEqualTo(0);
    }
}
