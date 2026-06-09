package com.onesley.oneclick.modules.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L4 « profondeur » — flux end-to-end {@code /api/events}.
 * Event CRUD + RSVP (participations) + listes elite + erreurs.
 */
class EventFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private String tenantId() {
        return jdbc.queryForObject("SELECT tenant_id::text FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1", String.class);
    }
    private String userId() {
        return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class);
    }

    /** Crée un CLIENT jetable via l'API admin (users isolés → ABAC déterministe, self-clean). */
    private String createClient(String admin) throws Exception {
        String roleId = jdbc.queryForObject("SELECT id::text FROM roles WHERE code='CLIENT' LIMIT 1", String.class);
        ResponseEntity<String> r = restTemplate.exchange(url("/api/users"), HttpMethod.POST, jsonJwtEntity(Map.of(
            "roleId", roleId, "email", "evt-abac-" + UUID.randomUUID() + "@x.ma",
            "phone", "+2126" + (1_000_0000 + (int) (Math.random() * 8_999_9999)),
            "password", "password1234", "firstName", "Evt", "lastName", "Abac"), admin), String.class);
        assertThat(r.getStatusCode().is2xxSuccessful())
            .as("création CLIENT jetable — reçu %s, body=%s", r.getStatusCode(), r.getBody()).isTrue();
        return om.readTree(r.getBody()).get("id").asText();
    }

    private String bearerCl(String userId) {
        return jwtIssuer.issueAccessToken(UUID.fromString(userId), "CLIENT").token();
    }

    /** Rend un client MEMBRE (palmeraie → rôle MEMBER) — P1.5 : RSVP est membre-only. */
    private void seedMembership(String userId) {
        jdbc.update(
            "INSERT INTO tenant_memberships (id, user_id, tenant_id, role_id, status, joined_at, created_at, updated_at) "
            + "SELECT gen_random_uuid(), ?::uuid, t.id, '10000000-0000-0000-0000-000000000006', 'active', now(), now(), now() "
            + "FROM tenants t WHERE t.slug = 'palmeraie' ON CONFLICT DO NOTHING", userId);
    }

    @Test
    void event_fullLifecycle_withRsvp() throws Exception {
        String admin = adminBearer();

        // CREATE
        ResponseEntity<String> post = restTemplate.exchange(url("/api/events"), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "tenantId", tenantId(), "title", "L4 Event",
                "eventAt", Instant.now().plus(7, ChronoUnit.DAYS).toString(),
                "capacity", 100, "isActive", true), admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id = om.readTree(post.getBody()).get("id").asText();

        // GET + list + PATCH
        assertThat(restTemplate.exchange(url("/api/events/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/events?page=0&size=5"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<String> patch = restTemplate.exchange(url("/api/events/" + id), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("title", "L4 Event Patched"), admin), String.class);
        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(patch.getBody()).get("title").asText()).isEqualTo("L4 Event Patched");

        // RSVP
        String uid = userId();
        assertThat(restTemplate.exchange(url("/api/events/participations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("eventId", id, "userId", uid, "status", "going"), admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(restTemplate.exchange(url("/api/events/" + id + "/participations"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/events/participations/by-user/" + uid), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/events/participations/by-event/" + id + "/user/" + uid),
            HttpMethod.DELETE, jwtEntity(admin), String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // elite lists
        assertThat(restTemplate.exchange(url("/api/events/elite/active"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.exchange(url("/api/events/elite/all"), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.OK);

        // DELETE → 404 (self-clean)
        assertThat(restTemplate.exchange(url("/api/events/" + id), HttpMethod.DELETE, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(restTemplate.exchange(url("/api/events/" + id), HttpMethod.GET, jwtEntity(admin), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void event_unknownId_404() {
        assertThat(restTemplate.exchange(url("/api/events/" + UUID.randomUUID()),
            HttpMethod.GET, jwtEntity(adminBearer()), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * Fuite de périmètre tenant (sécurité) — la liste {@code GET /api/events?tenantId=…} ne doit pas
     * exposer un programme (palmeraie/HOMU) dont le caller n'est pas membre actif.
     *
     * <ul>
     *   <li>CLIENT oneclick NON-MEMBRE de palmeraie, qui passe {@code tenantId=palmeraie} → 403
     *       (ForbiddenException : le param tenantId est hors de son périmètre visible).</li>
     *   <li>Après ajout d'une membership palmeraie active, le même {@code tenantId=palmeraie} → 200.</li>
     *   <li>SUPERADMIN (admin) → 200 quel que soit le tenantId (bypass cross-tenant).</li>
     *   <li>Sans tenantId, le CLIENT reçoit 200 (liste scopée à son périmètre visible côté service).</li>
     * </ul>
     */
    @Test
    void findAll_tenantIdOutOfScope_403_thenMemberOk() throws Exception {
        String admin = adminBearer();
        String palmeraieTenant = jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = 'palmeraie' LIMIT 1", String.class);

        String client = createClient(admin);            // home oneclick, PAS membre palmeraie
        String clientBearer = bearerCl(client);

        // NON-MEMBRE → tenantId=palmeraie hors périmètre → 403.
        assertThat(restTemplate.exchange(url("/api/events?tenantId=" + palmeraieTenant),
            HttpMethod.GET, jwtEntity(clientBearer), String.class).getStatusCode())
            .as("CLIENT non-membre palmeraie → tenantId palmeraie → 403").isEqualTo(HttpStatus.FORBIDDEN);

        // SUPERADMIN → bypass cross-tenant → 200.
        assertThat(restTemplate.exchange(url("/api/events?tenantId=" + palmeraieTenant),
            HttpMethod.GET, jwtEntity(admin), String.class).getStatusCode())
            .as("admin bypass cross-tenant → 200").isEqualTo(HttpStatus.OK);

        // Sans tenantId : la liste est scopée au périmètre visible → 200 (pas de 403).
        assertThat(restTemplate.exchange(url("/api/events?page=0&size=5"),
            HttpMethod.GET, jwtEntity(clientBearer), String.class).getStatusCode())
            .as("CLIENT liste sans tenantId → scopée → 200").isEqualTo(HttpStatus.OK);

        // Devient MEMBRE actif palmeraie → le même tenantId=palmeraie est désormais dans son périmètre → 200.
        // 1er load du cache après seed → reflète la membership (jamais authentifié auparavant pour ce token).
        seedMembership(client);
        String clientBearerMember = bearerCl(client);
        assertThat(restTemplate.exchange(url("/api/events?tenantId=" + palmeraieTenant),
            HttpMethod.GET, jwtEntity(clientBearerMember), String.class).getStatusCode())
            .as("CLIENT membre palmeraie → tenantId palmeraie → 200").isEqualTo(HttpStatus.OK);

        // self-clean
        jdbc.update("DELETE FROM tenant_memberships WHERE user_id = ?::uuid", UUID.fromString(client));
        restTemplate.exchange(url("/api/users/" + client), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void createEvent_invalidBody_400() {
        assertThat(restTemplate.exchange(url("/api/events"), HttpMethod.POST,
            jsonJwtEntity(Map.of("description", "sans titre ni date"), adminBearer()), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createEvent_asClient_403() {
        assertThat(restTemplate.exchange(url("/api/events"), HttpMethod.POST,
            jsonJwtEntity(Map.of("tenantId", tenantId(), "title", "X",
                "eventAt", Instant.now().plus(1, ChronoUnit.DAYS).toString()), bearerForRole("CLIENT")), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * V42 — RSVP est self-service (CREATE/DELETE:EVENT_RSVP), pas UPDATE:EVENTS (admin).
     * Un CLIENT peut s'inscrire/annuler LUI-MÊME, mais pas pour un AUTRE user
     * (ABAC requireOwnerOrAdmin → 403, corrige l'IDOR sur userId).
     */
    @Test
    void rsvp_asClient_selfOk_otherUser403() throws Exception {
        String admin = adminBearer();
        // Event actif créé par l'admin
        ResponseEntity<String> post = restTemplate.exchange(url("/api/events"), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "tenantId", tenantId(), "title", "V42 RSVP RBAC",
                "eventAt", Instant.now().plus(7, ChronoUnit.DAYS).toString(),
                "capacity", 50, "isActive", true), admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String eventId = om.readTree(post.getBody()).get("id").asText();

        // P1.5 : RSVP (CREATE:EVENT_RSVP) est membre-only → le client acteur doit être MEMBRE palmeraie.
        // Résolu via tenant_memberships (post-flip P3 : home oneclick mais membership palmeraie active).
        String clientId = jdbc.queryForObject(
            "SELECT tm.user_id::text FROM tenant_memberships tm JOIN tenants t ON t.id = tm.tenant_id "
            + "WHERE t.slug = 'palmeraie' AND tm.status = 'active' AND tm.deleted_at IS NULL "
            + "ORDER BY tm.user_id LIMIT 1", String.class);
        String clientBearer = jwtIssuer.issueAccessToken(UUID.fromString(clientId), "CLIENT").token();
        String otherUserId = jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL AND u.id::text <> ? LIMIT 1",
            String.class, clientId);

        // CLIENT s'inscrit LUI-MÊME → 201 (CREATE:EVENT_RSVP + owner)
        assertThat(restTemplate.exchange(url("/api/events/participations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("eventId", eventId, "userId", clientId, "status", "going"), clientBearer), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // CLIENT tente d'inscrire un AUTRE user → 403 (owner check)
        assertThat(restTemplate.exchange(url("/api/events/participations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("eventId", eventId, "userId", otherUserId, "status", "going"), clientBearer), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // CLIENT annule SA propre inscription → 204
        assertThat(restTemplate.exchange(url("/api/events/participations/by-event/" + eventId + "/user/" + clientId),
            HttpMethod.DELETE, jwtEntity(clientBearer), String.class).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // CLIENT tente d'annuler l'inscription d'un AUTRE user → 403 (owner check, avant le service)
        assertThat(restTemplate.exchange(url("/api/events/participations/by-event/" + eventId + "/user/" + otherUserId),
            HttpMethod.DELETE, jwtEntity(clientBearer), String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // cleanup
        restTemplate.exchange(url("/api/events/" + eventId), HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    /**
     * ABAC self-scope (Lot 0, niveau service) sur la stack réelle (filter chain → JwtDecoder →
     * UserRoleAuthoritiesConverter → @PreAuthorize → SecurityHelper → EventService → DB), avec
     * des CLIENTs jetables (isolés → déterministe, pas de RSVP résiduel du seed) :
     *
     * <ul>
     *   <li>CLIENT A RSVP pour LUI-MÊME → 201, et la participation créée est bien LA SIENNE
     *       ({@code userId} = A dans le DTO retourné + visible via {@code by-user/A}).</li>
     *   <li>CLIENT A tente d'annuler le RSVP de CLIENT B → 403 (ForbiddenException ABAC).</li>
     * </ul>
     */
    @Test
    void rsvp_disposableClient_selfScopedCreate_andForbiddenCancelOther() throws Exception {
        String admin = adminBearer();
        String clientA = createClient(admin);
        String clientB = createClient(admin);
        // P1.5 : RSVP membre-only → on rend A et B MEMBRES (palmeraie) pour qu'ils détiennent
        // CREATE/DELETE:EVENT_RSVP via le pliage de leur membership (sinon 403). Pas encore chargés
        // en cache (jamais authentifiés avant) → le 1er load reflètera la membership.
        seedMembership(clientA);
        seedMembership(clientB);
        String bearerA = bearerCl(clientA);
        String bearerB = bearerCl(clientB);

        // Event actif (capacité large) créé par l'admin.
        ResponseEntity<String> post = restTemplate.exchange(url("/api/events"), HttpMethod.POST,
            jsonJwtEntity(Map.of(
                "tenantId", tenantId(), "title", "ABAC RSVP self-scope",
                "eventAt", Instant.now().plus(7, ChronoUnit.DAYS).toString(),
                "capacity", 50, "isActive", true), admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String eventId = om.readTree(post.getBody()).get("id").asText();

        // A RSVP pour LUI-MÊME → 201 ; la participation créée porte SON userId.
        ResponseEntity<String> rsvpA = restTemplate.exchange(url("/api/events/participations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("eventId", eventId, "userId", clientA, "status", "going"), bearerA), String.class);
        assertThat(rsvpA.getStatusCode())
            .as("A RSVP self → 201, body=%s", rsvpA.getBody()).isEqualTo(HttpStatus.CREATED);
        JsonNode created = om.readTree(rsvpA.getBody());
        assertThat(created.get("userId").asText()).as("participation rattachée à A").isEqualTo(clientA);
        assertThat(created.get("eventId").asText()).isEqualTo(eventId);

        // …et A retrouve SA participation via by-user/A (lecture self autorisée).
        ResponseEntity<String> mine = restTemplate.exchange(
            url("/api/events/participations/by-user/" + clientA), HttpMethod.GET, jwtEntity(bearerA), String.class);
        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mine.getBody()).contains(eventId);

        // B RSVP pour LUI-MÊME (prépare la cible du test négatif).
        assertThat(restTemplate.exchange(url("/api/events/participations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("eventId", eventId, "userId", clientB, "status", "going"), bearerB), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // A tente d'annuler le RSVP de B → 403 (ABAC : annulation limitée à son propre compte).
        assertThat(restTemplate.exchange(
            url("/api/events/participations/by-event/" + eventId + "/user/" + clientB),
            HttpMethod.DELETE, jwtEntity(bearerA), String.class).getStatusCode())
            .as("A annule le RSVP de B → 403").isEqualTo(HttpStatus.FORBIDDEN);

        // self-clean : participations (CASCADE event delete), event, users jetables.
        restTemplate.exchange(url("/api/events/" + eventId), HttpMethod.DELETE, jwtEntity(admin), String.class);
        jdbc.update("DELETE FROM event_participations WHERE event_id = ?::uuid", UUID.fromString(eventId));
        for (String u : java.util.List.of(clientA, clientB)) {
            jdbc.update("DELETE FROM tenant_memberships WHERE user_id = ?::uuid", UUID.fromString(u));
            restTemplate.exchange(url("/api/users/" + u), HttpMethod.DELETE, jwtEntity(admin), String.class);
        }
    }
}
