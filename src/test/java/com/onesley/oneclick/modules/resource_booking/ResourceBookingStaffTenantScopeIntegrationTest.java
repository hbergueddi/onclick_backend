package com.onesley.oneclick.modules.resource_booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cause-racine du 403/liste vide sur l'admin des ressources d'un <b>owner PCC</b> : la gestion du parc
 * était gatée par {@code TenantScope.canSeeTenant(...)} (= memberships ∪ tenant public). Or un owner PCC
 * est <b>STAFF</b> de son tenant (palmeraie), <b>jamais membre</b> → palmeraie ∉ {@code visibleTenantIds}
 * → 403 / liste vide. Le fix aligne l'admin ressources sur le tenant <b>HOME</b> du staff (résolu serveur
 * via le JWT, calque exact de {@code GET /bookings/tenant}).
 *
 * <p>Ce test couvre le scénario réel : {@code owner@golfpcc.com} (RESTAURATEUR, home tenant = palmeraie)
 * crée / édite / toggle / supprime une ressource de palmeraie (2xx), ne peut PAS créer dans un autre
 * tenant (spoof → 403), et son board de gestion liste bien SES ressources. Un CLIENT reste refusé (403).</p>
 */
class ResourceBookingStaffTenantScopeIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private static final String RES = "/api/resource-bookings/resources";

    /** Id du tenant palmeraie (le tenant HOME des owners PCC). */
    private UUID palmeraieTenantId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = 'palmeraie' AND deleted_at IS NULL", String.class));
    }

    /** Un owner PCC réel (RESTAURATEUR dont le tenant HOME est palmeraie). */
    private UUID pccOwnerId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "JOIN tenants t ON t.id = u.tenant_id "
            + "WHERE r.code = 'RESTAURATEUR' AND t.slug = 'palmeraie' AND u.deleted_at IS NULL "
            + "ORDER BY u.email LIMIT 1", String.class));
    }

    /** Bearer signé pour l'owner PCC (authorities chargées du graphe role→permissions en DB). */
    private String pccOwnerBearer() {
        return jwtIssuer.issueAccessToken(pccOwnerId(), "RESTAURATEUR").token();
    }

    private void hardClean(String resourceId) {
        jdbc.update("DELETE FROM resource_booking_guests WHERE booking_id IN "
            + "(SELECT id FROM resource_bookings WHERE resource_id = ?::uuid)", resourceId);
        jdbc.update("DELETE FROM resource_bookings WHERE resource_id = ?::uuid", resourceId);
        jdbc.update("DELETE FROM resources WHERE id = ?::uuid", resourceId);
    }

    // ─── A. Cycle complet owner PCC sur SON tenant (palmeraie) : create/update/toggle/delete ──

    @Test
    void pccOwner_fullCycle_onOwnTenant_succeeds() throws Exception {
        String owner = pccOwnerBearer();
        String palmeraie = palmeraieTenantId().toString();

        // CREATE — sous le tenant palmeraie (le tenant HOME de l'owner) → 201.
        ResponseEntity<String> create = restTemplate.exchange(url(RES), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "tenantId", palmeraie, "resourceType", "padel",
                "name", "PCC Scope Court", "capacity", 4, "slotDurationMinutes", 60, "maxInvitees", 3),
                owner), String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = om.readTree(create.getBody());
        String resourceId = created.get("id").asText();
        // NB : tenant_id est insertable=false (peuplé par Hibernate au refresh) → le body POST peut le
        // porter à null ; le rattachement réel à palmeraie est vérifié par un GET frais (test B) et,
        // ci-dessous, par le simple fait que l'owner (tenant HOME palmeraie) peut éditer/toggler/supprimer.

        try {
            // GET /resources/{id} (lecture fraîche) : la ressource est bien rattachée à palmeraie (forcé serveur).
            ResponseEntity<String> get = restTemplate.exchange(url(RES + "/" + resourceId), HttpMethod.GET,
                jwtEntity(owner), String.class);
            assertThat(get.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(om.readTree(get.getBody()).get("tenantId").asText()).isEqualTo(palmeraie);

            // UPDATE (PATCH) — l'owner édite SA ressource → 200 + patch appliqué.
            ResponseEntity<String> upd = restTemplate.exchange(url(RES + "/" + resourceId), HttpMethod.PATCH,
                jsonJwtEntity(Map.of("capacity", 8, "name", "PCC Court Renommé"), owner), String.class);
            assertThat(upd.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(om.readTree(upd.getBody()).get("capacity").asInt()).isEqualTo(8);

            // TOGGLE (PATCH /enabled) true→false → 200.
            ResponseEntity<String> off = restTemplate.exchange(url(RES + "/" + resourceId + "/enabled"),
                HttpMethod.PATCH, jsonJwtEntity(Map.of("enabled", false), owner), String.class);
            assertThat(off.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(om.readTree(off.getBody()).get("enabled").asBoolean()).isFalse();

            // DELETE (pas de booking actif) → 204.
            assertThat(restTemplate.exchange(url(RES + "/" + resourceId), HttpMethod.DELETE,
                jwtEntity(owner), String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        } finally {
            hardClean(resourceId);
        }
    }

    // ─── B. Board de gestion : l'owner PCC liste bien SON parc (régression directe du bug) ──

    @Test
    void pccOwner_managementList_returnsOwnTenantResources() throws Exception {
        String owner = pccOwnerBearer();
        String palmeraie = palmeraieTenantId().toString();

        // Crée une ressource sous palmeraie.
        ResponseEntity<String> create = restTemplate.exchange(url(RES), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "tenantId", palmeraie, "resourceType", "spa", "name", "PCC Scope Cabine", "capacity", 2),
                owner), String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String resourceId = om.readTree(create.getBody()).get("id").asText();

        try {
            // GET /resources SANS tenantId : l'écran de gestion liste le parc du tenant HOME → non vide,
            // et toutes les lignes sont du tenant palmeraie (avant le fix : liste vide, faute de membership).
            ResponseEntity<String> list = restTemplate.exchange(
                url(RES + "?page=0&size=100"), HttpMethod.GET, jwtEntity(owner), String.class);
            assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
            JsonNode content = om.readTree(list.getBody()).get("content");
            assertThat(content.isArray()).isTrue();
            assertThat(content.size()).isGreaterThan(0);
            for (JsonNode res : content) {
                assertThat(res.get("tenantId").asText()).isEqualTo(palmeraie);
            }
        } finally {
            hardClean(resourceId);
        }
    }

    // ─── C. Anti-spoof : l'owner PCC ne crée pas dans un AUTRE tenant ──────────────────────

    @Test
    void pccOwner_createInForeignTenant_forbidden() {
        String owner = pccOwnerBearer();
        // tenantId d'un AUTRE tenant (oneclick) ≠ tenant HOME (palmeraie) → 403 (anti-spoof).
        String foreign = jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = 'oneclick' AND deleted_at IS NULL", String.class);
        ResponseEntity<String> create = restTemplate.exchange(url(RES), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "tenantId", foreign, "resourceType", "padel", "name", "Spoof", "capacity", 4), owner),
            String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ─── D. Un CLIENT ne gère JAMAIS le parc (RBAC inchangé) ───────────────────────────────

    @Test
    void client_createResource_forbidden() {
        ResponseEntity<String> create = restTemplate.exchange(url(RES), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "tenantId", palmeraieTenantId().toString(), "resourceType", "padel",
                "name", "X", "capacity", 4), bearerForRole("CLIENT")), String.class);
        assertThat(create.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
