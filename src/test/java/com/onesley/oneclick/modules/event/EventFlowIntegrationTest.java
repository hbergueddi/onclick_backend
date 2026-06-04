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

        String clientBearer = bearerForRole("CLIENT");
        // même requête que bearerForRole("CLIENT") → même user (sub du JWT)
        String clientId = jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL LIMIT 1", String.class);
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
            restTemplate.exchange(url("/api/users/" + u), HttpMethod.DELETE, jwtEntity(admin), String.class);
        }
    }
}
