package com.onesley.oneclick.modules.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E « profondeur » — contestation no-show (Feature #3) + pénalité (Feature #4) sur
 * la stack réelle (HTTP + JWT signés + DB).
 *
 * <p>Scénario complet :
 * <ol>
 *   <li>créer une réservation (owner = CLIENT réel) au restaurant d'un RESTAURATEUR réel ;</li>
 *   <li>marquer {@code no_show} (lateCancellation=false) → pénalité réputation appliquée ;</li>
 *   <li>le client conteste (phase resto) ;</li>
 *   <li>le restaurant refuse ;</li>
 *   <li>le délai passe (on backdate {@code no_show_marked_at} > 1h) → phase support ;</li>
 *   <li>le client re-conteste AVEC photo ;</li>
 *   <li>le support (admin) accepte → la pénalité no_show est reversée.</li>
 * </ol>
 *
 * <p>+ tests Feature #4 : {@code no_show} avec {@code lateCancellation=true} persiste le
 * flag ET rend la résa non-contestable (400).
 */
class NoShowDisputeFlowIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    private UUID createdReservationId;

    @AfterEach
    void cleanup() {
        if (createdReservationId != null) {
            jdbc.update("DELETE FROM no_show_disputes WHERE reservation_id = ?", createdReservationId);
            jdbc.update("DELETE FROM client_ratings WHERE reservation_id = ?", createdReservationId);
            jdbc.update("DELETE FROM reservation_status_histories WHERE reservation_id = ?", createdReservationId);
            jdbc.update("DELETE FROM reservations WHERE id = ?", createdReservationId);
            createdReservationId = null;
        }
    }

    /** restaurateur + son restaurant + tenant, distinct du client. */
    private Map<String, Object> restaurateurContext() {
        return jdbc.queryForMap("""
            SELECT rs.user_id::text AS restaurateurId, rs.restaurant_id::text AS restaurantId,
                   res.tenant_id::text AS tenantId
              FROM restaurant_staffs rs
              JOIN users u ON u.id = rs.user_id
              JOIN roles r ON r.id = u.role_id
              JOIN restaurants res ON res.id = rs.restaurant_id
             WHERE r.code = 'RESTAURATEUR' AND rs.deleted_at IS NULL
               AND u.deleted_at IS NULL AND res.deleted_at IS NULL
             ORDER BY rs.user_id LIMIT 1
            """);
    }

    /** un CLIENT réel qui n'est pas staff du restaurant donné. */
    private String clientNotStaffOf(String restaurantId) {
        return jdbc.queryForObject("""
            SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id
             WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL
               AND u.id NOT IN (SELECT user_id FROM restaurant_staffs WHERE restaurant_id = ?::uuid AND deleted_at IS NULL)
             LIMIT 1
            """, String.class, restaurantId);
    }

    private UUID createNoShowReservation(String tenantId, String clientId, String restaurantId, String admin) throws Exception {
        ResponseEntity<String> post = restTemplate.exchange(url("/api/reservations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("tenantId", tenantId, "clientId", clientId, "restaurantId", restaurantId,
                "reservationAt", Instant.now().plus(2, ChronoUnit.DAYS).toString(), "guestCount", 2,
                "notes", "dispute-e2e"), admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID id = UUID.fromString(om.readTree(post.getBody()).get("id").asText());
        this.createdReservationId = id;
        return id;
    }

    @Test
    void fullDisputeLifecycle_refusedThenRecontestedThenAccepted_reversesPenalty() throws Exception {
        String admin = adminBearer();
        Map<String, Object> ctx = restaurateurContext();
        String restaurateurId = (String) ctx.get("restaurateurId");
        String restaurantId = (String) ctx.get("restaurantId");
        String tenantId = (String) ctx.get("tenantId");
        String clientId = clientNotStaffOf(restaurantId);

        String clientBearer = jwtIssuer.issueAccessToken(UUID.fromString(clientId), "CLIENT").token();
        String restoBearer = jwtIssuer.issueAccessToken(UUID.fromString(restaurateurId), "RESTAURATEUR").token();

        UUID resaId = createNoShowReservation(tenantId, clientId, restaurantId, admin);

        // 2. Marquer no_show (lateCancellation=false) → flag + no_show_marked_at + pénalité.
        ResponseEntity<String> noShow = restTemplate.exchange(url("/api/reservations/" + resaId + "/status"),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("status", "no_show", "lateCancellation", false), admin), String.class);
        assertThat(noShow.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(noShow.getBody()).get("lateCancellation").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT no_show_marked_at IS NOT NULL FROM reservations WHERE id = ?",
            Boolean.class, resaId)).isTrue();

        // 3. Le client conteste (phase resto, < 1h).
        ResponseEntity<String> contest = restTemplate.exchange(url("/api/reservations/" + resaId + "/disputes"),
            HttpMethod.POST, jsonJwtEntity(Map.of("reason", "j'étais bien présent à l'heure"), clientBearer), String.class);
        assertThat(contest.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(om.readTree(contest.getBody()).get("escalationPhase").asText()).isEqualTo("resto");
        UUID dispute1 = UUID.fromString(om.readTree(contest.getBody()).get("id").asText());

        // 4. Le restaurant refuse (phase resto → staff resto autorisé).
        ResponseEntity<String> refuse = restTemplate.exchange(url("/api/disputes/" + dispute1),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("status", "refused", "resolutionNote", "table libérée 0 client"), restoBearer), String.class);
        assertThat(refuse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(refuse.getBody()).get("status").asText()).isEqualTo("refused");

        // 5. Le délai passe : on backdate no_show_marked_at à 2h → la prochaine contestation = phase support.
        jdbc.update("UPDATE reservations SET no_show_marked_at = now() - interval '2 hours' WHERE id = ?", resaId);

        // 6. Re-contestation après refus : photo OBLIGATOIRE.
        // 6a. sans photo → 400
        int noPhoto = restTemplate.exchange(url("/api/reservations/" + resaId + "/disputes"),
            HttpMethod.POST, jsonJwtEntity(Map.of("reason", "je re-conteste"), clientBearer), String.class)
            .getStatusCode().value();
        assertThat(noPhoto).isEqualTo(400);
        // 6b. avec photo → 201, phase support
        ResponseEntity<String> recontest = restTemplate.exchange(url("/api/reservations/" + resaId + "/disputes"),
            HttpMethod.POST, jsonJwtEntity(Map.of("reason", "preuve à l'appui",
                "photoUrl", "https://photos.example/ticket.jpg"), clientBearer), String.class);
        assertThat(recontest.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(om.readTree(recontest.getBody()).get("escalationPhase").asText()).isEqualTo("support");
        UUID dispute2 = UUID.fromString(om.readTree(recontest.getBody()).get("id").asText());

        // 6c. en phase support, le restaurant ne peut PLUS résoudre → 403
        int restoSupport = restTemplate.exchange(url("/api/disputes/" + dispute2), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "accepted"), restoBearer), String.class).getStatusCode().value();
        assertThat(restoSupport).isEqualTo(403);

        // 7. Le support (admin) accepte → event → loyalty reverse la pénalité.
        ResponseEntity<String> accept = restTemplate.exchange(url("/api/disputes/" + dispute2),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("status", "accepted", "resolutionNote", "preuve valide"), admin), String.class);
        assertThat(accept.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(accept.getBody()).get("status").asText()).isEqualTo("accepted");

        // Vérif DB : la dispute 2 est accepted.
        assertThat(jdbc.queryForObject("SELECT status FROM no_show_disputes WHERE id = ?", String.class, dispute2))
            .isEqualTo("accepted");

        // Vérif pénalité reversée : un client_ratings reason='dispute_accepted' apparaît (listener async).
        boolean reversed = awaitRatingReason(resaId, "dispute_accepted");
        assertThat(reversed).as("pénalité no_show reversée via listener loyalty (reason dispute_accepted)").isTrue();
    }

    @Test
    void noShow_lateCancellation_persistsFlag_andBlocksDispute() throws Exception {
        String admin = adminBearer();
        Map<String, Object> ctx = restaurateurContext();
        String restaurantId = (String) ctx.get("restaurantId");
        String tenantId = (String) ctx.get("tenantId");
        String clientId = clientNotStaffOf(restaurantId);
        String clientBearer = jwtIssuer.issueAccessToken(UUID.fromString(clientId), "CLIENT").token();

        UUID resaId = createNoShowReservation(tenantId, clientId, restaurantId, admin);

        // no_show avec lateCancellation=true → flag persiste.
        ResponseEntity<String> noShow = restTemplate.exchange(url("/api/reservations/" + resaId + "/status"),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("status", "no_show", "lateCancellation", true), admin), String.class);
        assertThat(noShow.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(noShow.getBody()).get("lateCancellation").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT late_cancellation FROM reservations WHERE id = ?", Boolean.class, resaId)).isTrue();

        // Contestation impossible (annulation tardive → non contestable) → 400.
        int blocked = restTemplate.exchange(url("/api/reservations/" + resaId + "/disputes"),
            HttpMethod.POST, jsonJwtEntity(Map.of("reason", "je conteste"), clientBearer), String.class)
            .getStatusCode().value();
        assertThat(blocked).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM no_show_disputes WHERE reservation_id = ?",
            Integer.class, resaId)).isZero();
    }

    @Test
    void noShow_appliesPenalty_ratingRecorded() throws Exception {
        String admin = adminBearer();
        Map<String, Object> ctx = restaurateurContext();
        String restaurantId = (String) ctx.get("restaurantId");
        String tenantId = (String) ctx.get("tenantId");
        String clientId = clientNotStaffOf(restaurantId);

        UUID resaId = createNoShowReservation(tenantId, clientId, restaurantId, admin);

        restTemplate.exchange(url("/api/reservations/" + resaId + "/status"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "no_show"), admin), String.class);

        // Le listener loyalty enregistre une notation reason='no_show' (pénalité) — async.
        assertThat(awaitRatingReason(resaId, "no_show"))
            .as("pénalité no_show appliquée via listener loyalty").isTrue();
    }

    /** Le client ne peut pas contester le no_show d'une AUTRE personne (ABAC owner) → 403. */
    @Test
    void contest_notOwner_returns403() throws Exception {
        String admin = adminBearer();
        Map<String, Object> ctx = restaurateurContext();
        String restaurantId = (String) ctx.get("restaurantId");
        String tenantId = (String) ctx.get("tenantId");
        String ownerId = clientNotStaffOf(restaurantId);

        UUID resaId = createNoShowReservation(tenantId, ownerId, restaurantId, admin);
        restTemplate.exchange(url("/api/reservations/" + resaId + "/status"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "no_show"), admin), String.class);

        // Un AUTRE client (a CREATE:DISPUTES mais n'est pas owner) → 403 (ABAC).
        String otherClientId = jdbc.queryForObject("""
            SELECT u.id::text FROM users u JOIN roles r ON r.id=u.role_id
             WHERE r.code='CLIENT' AND u.deleted_at IS NULL AND u.id <> ?::uuid LIMIT 1
            """, String.class, ownerId);
        String otherBearer = jwtIssuer.issueAccessToken(UUID.fromString(otherClientId), "CLIENT").token();

        int status = restTemplate.exchange(url("/api/reservations/" + resaId + "/disputes"),
            HttpMethod.POST, jsonJwtEntity(Map.of("reason", "pas la mienne"), otherBearer), String.class)
            .getStatusCode().value();
        assertThat(status).isEqualTo(403);
    }

    /**
     * Attend (bounded) qu'un {@code client_ratings} avec le {@code reason} donné apparaisse
     * pour la réservation — le listener loyalty est async ({@code @ApplicationModuleListener}
     * = after-commit + @Async).
     */
    private boolean awaitRatingReason(UUID reservationId, String reason) {
        for (int i = 0; i < 40; i++) { // ~8s max
            Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM client_ratings WHERE reservation_id = ? AND reason = ?",
                Integer.class, reservationId, reason);
            if (count != null && count > 0) return true;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
