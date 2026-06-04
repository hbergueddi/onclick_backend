package com.onesley.oneclick.modules.resource_booking;

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
 * RBAC/ABAC L4 — split d'autorité {@code BOOKINGS} (membre) vs {@code RESOURCE_BOOKINGS}
 * (gestion du parc) introduit par la migration V66 (PCC Lot 0) + endpoint busy-slots (Lot 1).
 *
 * <p>Vérifie l'invariant de sécurité central : un CLIENT peut RÉSERVER (CREATE:BOOKINGS) mais
 * ne peut PAS créer de ressource (CREATE:RESOURCE_BOOKINGS) → pas d'escalade. Le self-scope ABAC
 * force l'organisateur d'un booking membre. Le staff/admin confirme. busy-slots ne fuite aucune PII.</p>
 */
class ResourceBookingRbacIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private String tenantId() {
        return jdbc.queryForObject(
            "SELECT tenant_id::text FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1", String.class);
    }

    /** Un CLIENT réel, déterministe (ORDER BY id) — l'id sert AUSSI de sub du JWT {@link #clientBearer()}. */
    private UUID clientUserId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL ORDER BY u.id LIMIT 1", String.class));
    }

    /**
     * Bearer CLIENT dont le {@code sub} == {@link #clientUserId()} EXACTEMENT — on contrôle l'id
     * (vs {@code bearerForRole} qui fait un LIMIT 1 sans ORDER → potentiellement un autre CLIENT).
     * Indispensable pour asserter le self-scope (organizer forcé = ce sub précis).
     */
    private String clientBearer() {
        return jwtIssuer.issueAccessToken(clientUserId(), "CLIENT").token();
    }

    /** Crée une ressource (côté parc, admin) et renvoie son id. */
    private String createResourceAsAdmin(String admin) throws Exception {
        ResponseEntity<String> rPost = restTemplate.exchange(url("/api/resource-bookings/resources"),
            HttpMethod.POST, jsonJwtEntity(Map.of(
                "tenantId", tenantId(), "resourceType", "padel", "name", "RBAC Court", "capacity", 4), admin),
            String.class);
        assertThat(rPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return om.readTree(rPost.getBody()).get("id").asText();
    }

    private Map<String, Object> bookingBody(String resourceId, UUID organizerId) {
        return Map.of(
            "resourceId", resourceId,
            "organizerId", organizerId.toString(),
            "startAt", Instant.now().plus(3, ChronoUnit.DAYS).toString(),
            "endAt", Instant.now().plus(3, ChronoUnit.DAYS).plus(90, ChronoUnit.MINUTES).toString(),
            "status", "pending");
    }

    // ─── A. Le split empêche l'escalade ──────────────────────────────────────────

    @Test
    void client_canCreateOwnBooking_butCannotCreateResource() throws Exception {
        String admin = adminBearer();
        String client = clientBearer();
        String resourceId = createResourceAsAdmin(admin);

        // CLIENT crée SON booking → 201 (détient CREATE:BOOKINGS depuis V66)
        ResponseEntity<String> bPost = restTemplate.exchange(url("/api/resource-bookings/bookings"),
            HttpMethod.POST, jsonJwtEntity(bookingBody(resourceId, clientUserId()), client), String.class);
        assertThat(bPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // CLIENT tente de créer une RESSOURCE → 403 (ne détient PAS CREATE:RESOURCE_BOOKINGS)
        ResponseEntity<String> rPost = restTemplate.exchange(url("/api/resource-bookings/resources"),
            HttpMethod.POST, jsonJwtEntity(Map.of(
                "tenantId", tenantId(), "resourceType", "padel", "name", "Hack Court"), client), String.class);
        assertThat(rPost.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // CLIENT tente de créer un TARIF → 403 (gestion parc CREATE:RESOURCE_BOOKINGS)
        ResponseEntity<String> pPost = restTemplate.exchange(url("/api/resource-bookings/pricings"),
            HttpMethod.POST, jsonJwtEntity(Map.of(
                "resourceId", resourceId, "name", "90min", "price", 200), client), String.class);
        assertThat(pPost.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // self-clean
        restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId),
            HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void client_canStillListResources_keepsViewResourceBookings() {
        // GET /resources reste VIEW:RESOURCE_BOOKINGS → le CLIENT le garde (V32) pour lister
        // les ressources réservables. V66 n'a RIEN retiré.
        ResponseEntity<String> resp = restTemplate.exchange(
            url("/api/resource-bookings/resources?page=0&size=5"),
            HttpMethod.GET, jwtEntity(clientBearer()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ─── B. ABAC self-scope : un membre ne réserve QUE pour lui ───────────────────

    @Test
    void client_bookingForAnotherOrganizer_isForcedToSelf() throws Exception {
        String admin = adminBearer();
        String client = clientBearer();
        String resourceId = createResourceAsAdmin(admin);

        UUID someoneElse = UUID.randomUUID(); // le CLIENT tente d'inscrire un AUTRE organisateur
        ResponseEntity<String> bPost = restTemplate.exchange(url("/api/resource-bookings/bookings"),
            HttpMethod.POST, jsonJwtEntity(bookingBody(resourceId, someoneElse), client), String.class);

        // Pas d'erreur : le service force organizer_id = soi (anti-spoof), et NE crée PAS
        // pour someoneElse. Le booking renvoyé porte l'id du CLIENT, pas le spoof.
        assertThat(bPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode body = om.readTree(bPost.getBody());
        assertThat(body.get("organizerId").asText()).isEqualTo(clientUserId().toString());
        assertThat(body.get("organizerId").asText()).isNotEqualTo(someoneElse.toString());

        restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId),
            HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    // ─── C. Le staff/admin confirme un booking ────────────────────────────────────

    @Test
    void staffAdmin_canConfirmBooking() throws Exception {
        String admin = adminBearer();
        String client = clientBearer();
        String resourceId = createResourceAsAdmin(admin);

        // Le membre crée une demande (pending)
        ResponseEntity<String> bPost = restTemplate.exchange(url("/api/resource-bookings/bookings"),
            HttpMethod.POST, jsonJwtEntity(bookingBody(resourceId, clientUserId()), client), String.class);
        assertThat(bPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bookingId = om.readTree(bPost.getBody()).get("id").asText();

        // L'admin confirme (UPDATE:BOOKINGS + isStaffOrAdmin → pas de filtre self) → 200
        ResponseEntity<String> patchAdmin = restTemplate.exchange(url("/api/resource-bookings/bookings/" + bookingId),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("status", "confirmed"), admin), String.class);
        assertThat(patchAdmin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(patchAdmin.getBody()).get("status").asText()).isEqualTo("confirmed");

        // RESTAURATEUR (staff opérationnel) peut aussi marquer honored → 200
        ResponseEntity<String> patchResto = restTemplate.exchange(url("/api/resource-bookings/bookings/" + bookingId),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("status", "completed"), bearerForRole("RESTAURATEUR")), String.class);
        assertThat(patchResto.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(patchResto.getBody()).get("status").asText()).isEqualTo("completed");

        restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId),
            HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    // ─── D. busy-slots : disponibilité sans PII ────────────────────────────────────

    @Test
    void busySlots_returnsOnlyStartEnd_noPii() throws Exception {
        String admin = adminBearer();
        String client = clientBearer();
        String resourceId = createResourceAsAdmin(admin);

        Instant start = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        Instant end = start.plus(90, ChronoUnit.MINUTES);
        ResponseEntity<String> bPost = restTemplate.exchange(url("/api/resource-bookings/bookings"),
            HttpMethod.POST, jsonJwtEntity(Map.of(
                "resourceId", resourceId, "organizerId", clientUserId().toString(),
                "startAt", start.toString(), "endAt", end.toString(),
                "status", "confirmed", "notes", "secret-pii-note"), client), String.class);
        assertThat(bPost.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String date = start.atZone(java.time.ZoneOffset.UTC).toLocalDate().toString();

        // Le CLIENT (VIEW:BOOKINGS) consulte la disponibilité → 200
        ResponseEntity<String> busy = restTemplate.exchange(
            url("/api/resource-bookings/resources/" + resourceId + "/busy-slots?date=" + date),
            HttpMethod.GET, jwtEntity(client), String.class);
        assertThat(busy.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode arr = om.readTree(busy.getBody());
        assertThat(arr.isArray()).isTrue();
        assertThat(arr).hasSizeGreaterThanOrEqualTo(1);
        JsonNode slot = arr.get(0);
        // EXACTEMENT startAt + endAt — aucune PII (organizer, invités, notes, statut absents).
        assertThat(slot.fieldNames()).toIterable().containsExactlyInAnyOrder("startAt", "endAt");
        assertThat(busy.getBody()).doesNotContain("organizerId", "secret-pii-note", "notes");

        restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId),
            HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    @Test
    void busySlots_emptyDay_returnsEmptyArray() throws Exception {
        String admin = adminBearer();
        String resourceId = createResourceAsAdmin(admin);
        // Jour lointain sans booking → tableau vide (200).
        ResponseEntity<String> busy = restTemplate.exchange(
            url("/api/resource-bookings/resources/" + resourceId + "/busy-slots?date=2030-01-01"),
            HttpMethod.GET, jwtEntity(clientBearer()), String.class);
        assertThat(busy.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(busy.getBody()).isArray()).isTrue();
        assertThat(om.readTree(busy.getBody())).isEmpty();

        restTemplate.exchange(url("/api/resource-bookings/resources/" + resourceId),
            HttpMethod.DELETE, jwtEntity(admin), String.class);
    }

    // ─── E. 401 sans JWT ───────────────────────────────────────────────────────────

    @Test
    void noJwt_returns401() {
        // POST /bookings sans bearer → 401 (filter chain OAuth2)
        assertThat(restTemplate.exchange(url("/api/resource-bookings/bookings"),
            HttpMethod.POST, jsonJwtEntity(Map.of(
                "resourceId", UUID.randomUUID().toString(), "organizerId", UUID.randomUUID().toString(),
                "startAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                "endAt", Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS).toString()), null),
            String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // GET busy-slots sans bearer → 401
        assertThat(restTemplate.exchange(
            url("/api/resource-bookings/resources/" + UUID.randomUUID() + "/busy-slots?date=2030-01-01"),
            HttpMethod.GET, jwtEntity(null), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
