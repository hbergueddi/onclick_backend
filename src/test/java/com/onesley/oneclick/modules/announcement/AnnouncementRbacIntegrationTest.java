package com.onesley.oneclick.modules.announcement;

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
 * RBAC/ABAC L4 — « Annonces tenant » (Lot 8, ressource {@code ANNOUNCEMENTS} seedée par V70).
 *
 * <p>Invariants vérifiés sur la stack réelle (filter chain → JwtDecoder → converter →
 * {@code @PreAuthorize} → service ABAC → DB) :
 * <ul>
 *   <li>Sans JWT → 401.</li>
 *   <li>CLIENT (membre PCC) → 403 sur GET et POST (pas de VIEW/CREATE:ANNOUNCEMENTS).</li>
 *   <li>tenant-admin (owner PCC, RESTAURATEUR scopé palmeraie) : flow create → list → mark-read,
 *       et l'édition du corps bump body_version + repasse readByMe à false (ré-acquittement).</li>
 *   <li>enforce-max-pinned : créer 2 annonces épinglées de même priorité → seule la 2e reste pinned.</li>
 *   <li>tenant-scope : un RESTAURATEUR d'un AUTRE tenant (HOMU) → 403 sur PATCH/DELETE d'une
 *       annonce palmeraie (a UPDATE:ANNOUNCEMENTS au RBAC, mais ABAC service : pas tenant-admin du
 *       tenant de l'annonce).</li>
 *   <li>archive + soft-delete par l'admin → 200/204.</li>
 * </ul>
 *
 * <p>Données : owners PCC seedés ({@code owner@*pcc.com}, RESTAURATEUR, tenant palmeraie, staff des
 * restos palmeraie) + membres ({@code memberN@palmeraie.com}, CLIENT). Les annonces créées par le
 * test sont supprimées (hard) en teardown.</p>
 */
class AnnouncementRbacIntegrationTest extends AbstractIntegrationTest {

    private static final String PALMERAIE_OWNER = "owner@padelpcc.com"; // RESTAURATEUR + tenant palmeraie
    private static final String PALMERAIE_OWNER_2 = "owner@golfpcc.com"; // autre owner palmeraie (lecture/ack)
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

    private UUID tenantIdBySlug(String slug) {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = ?", String.class, slug));
    }

    @AfterEach
    void cleanup() {
        for (UUID id : createdIds) {
            jdbc.update("DELETE FROM announcement_reads WHERE announcement_id = ?", id);
            jdbc.update("DELETE FROM tenant_announcements WHERE id = ?", id);
        }
        createdIds.clear();
    }

    // ─── A. 401 sans JWT ─────────────────────────────────────────────────────────

    @Test
    void noJwt_returns401() {
        assertThat(restTemplate.exchange(url("/api/announcements"),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.exchange(url("/api/announcements"),
            HttpMethod.POST, jsonJwtEntity("{\"title\":\"T\",\"body\":\"B\"}", null), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ─── B. RBAC/ABAC membre (V97) : CLIENT membre LIT (GET 200) + acquitte, mais N'ÉCRIT pas ──────

    @Test
    void member_canViewPublished_butCannotCreateOrEditOrDelete() throws Exception {
        String member = bearerFor(PALMERAIE_MEMBER); // CLIENT, membership active palmeraie

        // GET / → 200 (V97 : VIEW:ANNOUNCEMENTS accordé à CLIENT ; ABAC service → publiées vivantes
        // du tenant programme). Contient l'annonce seed palmeraie « Bienvenue sur les annonces PCC ».
        ResponseEntity<String> list = restTemplate.exchange(url("/api/announcements"),
            HttpMethod.GET, jwtEntity(member), String.class);
        assertThat(list.getStatusCode())
            .as("membre GET — reçu %s, body=%s", list.getStatusCode(), list.getBody())
            .isEqualTo(HttpStatus.OK);
        JsonNode arr = om.readTree(list.getBody());
        assertThat(arr.isArray()).isTrue();
        assertThat(arr).as("le membre voit au moins l'annonce publiée du tenant (seed V70)").isNotEmpty();
        // Le membre ne voit JAMAIS de programmée/archivée — uniquement publiées vivantes.
        for (JsonNode n : arr) {
            assertThat(n.get("published").asBoolean()).isTrue();
            assertThat(n.get("archivedAt").isNull()).isTrue();
        }

        // Écriture interdite : CLIENT n'a ni CREATE ni UPDATE ni DELETE:ANNOUNCEMENTS → 403.
        UUID anyId = UUID.fromString(arr.get(0).get("id").asText());
        assertThat(restTemplate.exchange(url("/api/announcements"),
            HttpMethod.POST, jsonJwtEntity("{\"title\":\"T\",\"body\":\"B\"}", member), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(url("/api/announcements/" + anyId),
            HttpMethod.PATCH, jsonJwtEntity("{\"title\":\"X\",\"body\":\"Y\"}", member), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(restTemplate.exchange(url("/api/announcements/" + anyId),
            HttpMethod.DELETE, jwtEntity(member), String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void member_canMarkReadPublishedAnnouncement() throws Exception {
        String member = bearerFor(PALMERAIE_MEMBER);
        ResponseEntity<String> list = restTemplate.exchange(url("/api/announcements"),
            HttpMethod.GET, jwtEntity(member), String.class);
        JsonNode arr = om.readTree(list.getBody());
        org.junit.jupiter.api.Assumptions.assumeTrue(arr.isArray() && !arr.isEmpty(),
            "nécessite ≥1 annonce publiée pour le tenant palmeraie (seed V70)");
        UUID id = UUID.fromString(arr.get(0).get("id").asText());

        // mark-read par le membre → 200, readByMe=true (V97 : canReadInTenant accepte le membre actif).
        ResponseEntity<String> readResp = restTemplate.exchange(url("/api/announcements/" + id + "/read"),
            HttpMethod.POST, jsonJwtEntity("{}", member), String.class);
        assertThat(readResp.getStatusCode())
            .as("membre mark-read — reçu %s, body=%s", readResp.getStatusCode(), readResp.getBody())
            .isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(readResp.getBody()).get("readByMe").asBoolean()).isTrue();
        // Cleanup : retire l'acquittement du membre (ne pas polluer le seed partagé).
        jdbc.update("DELETE FROM announcement_reads WHERE user_id = ?", userIdByEmail(PALMERAIE_MEMBER));
    }

    // ─── C. tenant-admin flow : create → list → mark-read → édition (ré-ack) ──────

    @Test
    void tenantAdmin_create_list_markRead_editResetsRead_flow() throws Exception {
        String owner = bearerFor(PALMERAIE_OWNER);
        UUID ownerId = userIdByEmail(PALMERAIE_OWNER);

        // 1. create (permanente, épinglée, publiée) → 201.
        ResponseEntity<String> createResp = restTemplate.exchange(url("/api/announcements"),
            HttpMethod.POST,
            jsonJwtEntity("{\"title\":\"Maintenance piscine\",\"body\":\"Piscine fermée jeudi matin.\",\"priority\":\"permanent\",\"pinned\":true}", owner),
            String.class);
        assertThat(createResp.getStatusCode())
            .as("create — reçu %s, body=%s", createResp.getStatusCode(), createResp.getBody())
            .isEqualTo(HttpStatus.CREATED);
        JsonNode created = om.readTree(createResp.getBody());
        UUID id = UUID.fromString(created.get("id").asText());
        createdIds.add(id);
        assertThat(created.get("authorId").asText()).isEqualTo(ownerId.toString());
        assertThat(created.get("pinned").asBoolean()).isTrue();
        assertThat(created.get("published").asBoolean()).isTrue();
        assertThat(created.get("bodyVersion").asInt()).isEqualTo(1);

        // 2. GET / (owner = tenant-admin → voit tout) contient l'annonce, readByMe=false.
        ResponseEntity<String> list = restTemplate.exchange(url("/api/announcements"),
            HttpMethod.GET, jwtEntity(owner), String.class);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody()).contains(id.toString());

        // 3. mark-read (le caller acquitte la version 1) → 200, readByMe=true.
        ResponseEntity<String> readResp = restTemplate.exchange(url("/api/announcements/" + id + "/read"),
            HttpMethod.POST, jsonJwtEntity("{\"bodyVersion\":1}", owner), String.class);
        assertThat(readResp.getStatusCode())
            .as("mark-read — reçu %s, body=%s", readResp.getStatusCode(), readResp.getBody())
            .isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(readResp.getBody()).get("readByMe").asBoolean()).isTrue();

        // 4. édition DU CORPS → bump body_version (1 → 2) + reset reads → readByMe repasse à false.
        ResponseEntity<String> editResp = restTemplate.exchange(url("/api/announcements/" + id),
            HttpMethod.PATCH,
            jsonJwtEntity("{\"title\":\"Maintenance piscine\",\"body\":\"Piscine fermée jeudi TOUTE la journée.\",\"priority\":\"permanent\",\"pinned\":true}", owner),
            String.class);
        assertThat(editResp.getStatusCode())
            .as("edit — reçu %s, body=%s", editResp.getStatusCode(), editResp.getBody())
            .isEqualTo(HttpStatus.OK);
        JsonNode edited = om.readTree(editResp.getBody());
        assertThat(edited.get("bodyVersion").asInt()).isEqualTo(2);
        assertThat(edited.get("readByMe").asBoolean())
            .as("après édition du corps, l'acquittement v1 ne couvre plus v2")
            .isFalse();
    }

    // ─── D. enforce-max-pinned : 2e épinglée même priorité désépingle la 1re ──────

    @Test
    void create_secondPinnedSamePriority_unpinsFirst() throws Exception {
        String owner = bearerFor(PALMERAIE_OWNER);

        ResponseEntity<String> first = restTemplate.exchange(url("/api/announcements"),
            HttpMethod.POST,
            jsonJwtEntity("{\"title\":\"Annonce A\",\"body\":\"Corps A\",\"priority\":\"urgent\",\"pinned\":true}", owner),
            String.class);
        UUID idA = UUID.fromString(om.readTree(first.getBody()).get("id").asText());
        createdIds.add(idA);

        ResponseEntity<String> second = restTemplate.exchange(url("/api/announcements"),
            HttpMethod.POST,
            jsonJwtEntity("{\"title\":\"Annonce B\",\"body\":\"Corps B\",\"priority\":\"urgent\",\"pinned\":true}", owner),
            String.class);
        UUID idB = UUID.fromString(om.readTree(second.getBody()).get("id").asText());
        createdIds.add(idB);

        // La 1re a été désépinglée (max 1 épinglée par priorité/tenant).
        Boolean pinnedA = jdbc.queryForObject(
            "SELECT is_pinned FROM tenant_announcements WHERE id = ?", Boolean.class, idA);
        Boolean pinnedB = jdbc.queryForObject(
            "SELECT is_pinned FROM tenant_announcements WHERE id = ?", Boolean.class, idB);
        assertThat(pinnedA).as("la 1re annonce urgente doit être désépinglée").isFalse();
        assertThat(pinnedB).as("la 2e annonce urgente reste épinglée").isTrue();
    }

    // ─── E. tenant-scope : un owner d'un AUTRE tenant ne peut PATCH/DELETE ────────

    @Test
    void crossTenantOwner_cannotEditOrDelete_palmeraieAnnouncement() throws Exception {
        String palmeraieOwner = bearerFor(PALMERAIE_OWNER);
        String homuOwner = bearerFor(HOMU_OWNER); // RESTAURATEUR d'un AUTRE tenant

        // Annonce palmeraie créée par le owner palmeraie.
        ResponseEntity<String> createResp = restTemplate.exchange(url("/api/announcements"),
            HttpMethod.POST,
            jsonJwtEntity("{\"title\":\"Interne PCC\",\"body\":\"Réservé PCC\",\"priority\":\"permanent\",\"pinned\":false}", palmeraieOwner),
            String.class);
        UUID id = UUID.fromString(om.readTree(createResp.getBody()).get("id").asText());
        createdIds.add(id);

        // L'owner HOMU a UPDATE/DELETE:ANNOUNCEMENTS (RBAC) mais PAS sur le tenant palmeraie (ABAC).
        assertThat(restTemplate.exchange(url("/api/announcements/" + id),
            HttpMethod.PATCH,
            jsonJwtEntity("{\"title\":\"Pirate\",\"body\":\"Pirate\",\"priority\":\"permanent\",\"pinned\":false}", homuOwner),
            String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(restTemplate.exchange(url("/api/announcements/" + id),
            HttpMethod.DELETE, jwtEntity(homuOwner), String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);

        // L'annonce existe toujours (non supprimée).
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM tenant_announcements WHERE id = ? AND deleted_at IS NULL",
            Integer.class, id);
        assertThat(cnt).isEqualTo(1);
    }

    // ─── F. admin global : archive + soft-delete → 200 / 204 ─────────────────────

    @Test
    void admin_archive_then_softDelete() throws Exception {
        String owner = bearerFor(PALMERAIE_OWNER);
        ResponseEntity<String> createResp = restTemplate.exchange(url("/api/announcements"),
            HttpMethod.POST,
            jsonJwtEntity("{\"title\":\"À archiver\",\"body\":\"Corps\",\"priority\":\"permanent\",\"pinned\":true}", owner),
            String.class);
        UUID id = UUID.fromString(om.readTree(createResp.getBody()).get("id").asText());
        createdIds.add(id);

        // archive (admin global SUPERADMIN → bypass tenant-admin) → 200, archivedAt posé, pinned=false.
        ResponseEntity<String> archiveResp = restTemplate.exchange(url("/api/announcements/" + id + "/archive"),
            HttpMethod.PATCH, jwtEntity(adminBearer()), String.class);
        assertThat(archiveResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode archived = om.readTree(archiveResp.getBody());
        assertThat(archived.get("archivedAt").isNull()).isFalse();
        assertThat(archived.get("pinned").asBoolean()).isFalse();

        // soft-delete → 204.
        assertThat(restTemplate.exchange(url("/api/announcements/" + id),
            HttpMethod.DELETE, jwtEntity(adminBearer()), String.class).getStatusCode())
            .isEqualTo(HttpStatus.NO_CONTENT);
        Integer alive = jdbc.queryForObject(
            "SELECT COUNT(*) FROM tenant_announcements WHERE id = ? AND deleted_at IS NULL",
            Integer.class, id);
        assertThat(alive).isEqualTo(0);
    }

    // ─── G. Lot 4b : super-admin publie pour un tenant CIBLE (POST /admin?tenantId) ──

    @Test
    void superAdmin_adminCreate_forTargetTenant_succeeds() throws Exception {
        UUID palmeraie = tenantIdBySlug("palmeraie");

        // Le super-admin (UPDATE:TENANTS) crée une annonce POUR le tenant palmeraie (cross-tenant),
        // sans avoir lui-même de tenant home. tenantId du DTO retourné = le tenant CIBLE.
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/announcements/admin?tenantId=" + palmeraie),
            HttpMethod.POST,
            jsonJwtEntity("{\"title\":\"Pilotage admin\",\"body\":\"Annonce poussée depuis le super-admin\",\"priority\":\"permanent\",\"pinned\":false}", adminBearer()),
            String.class);
        assertThat(resp.getStatusCode())
            .as("admin create — reçu %s, body=%s", resp.getStatusCode(), resp.getBody())
            .isEqualTo(HttpStatus.CREATED);
        JsonNode created = om.readTree(resp.getBody());
        UUID id = UUID.fromString(created.get("id").asText());
        createdIds.add(id);
        assertThat(created.get("tenantId").asText()).isEqualTo(palmeraie.toString());

        // L'annonce est bien lisible côté admin pour ce tenant (cross-tenant read existant).
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM tenant_announcements WHERE id = ? AND tenant_id = ?",
            Integer.class, id, palmeraie);
        assertThat(cnt).isEqualTo(1);
    }

    @Test
    void tenantAdmin_adminCreate_forbidden_lacksUpdateTenants() {
        UUID palmeraie = tenantIdBySlug("palmeraie");
        // Le owner palmeraie a CREATE:ANNOUNCEMENTS mais PAS UPDATE:TENANTS (autorité SUPERADMIN-only) →
        // l'endpoint admin cross-tenant lui est interdit (403).
        String owner = bearerFor(PALMERAIE_OWNER);
        assertThat(restTemplate.exchange(
            url("/api/announcements/admin?tenantId=" + palmeraie),
            HttpMethod.POST,
            jsonJwtEntity("{\"title\":\"X\",\"body\":\"Y\",\"priority\":\"permanent\",\"pinned\":false}", owner),
            String.class).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
