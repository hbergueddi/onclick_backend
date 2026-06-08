package com.onesley.oneclick.modules.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.modules.reservation.internal.ReservationCronJobs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L4 « profondeur » — contre-proposition de réservation (V89, {@code proposed_reservation_at}).
 *
 * <p>Couvre le cycle complet du nouveau créneau proposé par le restaurant via le PATCH
 * {@code /api/reservations/{id}/status} existant (garde RBAC {@code UPDATE:RESERVATIONS} + ABAC) :
 * <ul>
 *   <li>propose ({@code → counter_proposed}) exige un {@code proposedReservationAt} <b>futur</b> (sinon 400) ;</li>
 *   <li>le client est <b>notifié</b> (event {@code ReservationStatusChangedEvent} →
 *       {@code NotificationEventHandler}, libellé « Nouvelle proposition ») ;</li>
 *   <li>accept ({@code counter_proposed → confirmed}) recopie le créneau proposé dans
 *       {@code reservation_at} puis nettoie {@code proposed_reservation_at} ;</li>
 *   <li>decline ({@code → cancelled}) nettoie le créneau proposé ;</li>
 *   <li>sécurité : 401 sans bearer.</li>
 * </ul>
 */
class ReservationCounterProposalIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper om = new ObjectMapper();

    @Autowired
    private ReservationCronJobs cron;

    private String[] restoTenant() {
        return jdbc.queryForObject(
            "SELECT r.id::text || ',' || r.tenant_id::text FROM restaurants r "
            + "WHERE r.tenant_id IS NOT NULL AND r.deleted_at IS NULL LIMIT 1",
            String.class).split(",");
    }
    private String userId() {
        return jdbc.queryForObject("SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class);
    }

    /** Crée une réservation pending et renvoie son id. */
    private String createPending(String admin, String tenantId, String uid, String restaurantId) throws Exception {
        ResponseEntity<String> post = restTemplate.exchange(url("/api/reservations"), HttpMethod.POST,
            jsonJwtEntity(Map.of("tenantId", tenantId, "clientId", uid, "restaurantId", restaurantId,
                "reservationAt", Instant.now().plus(3, ChronoUnit.DAYS).toString(),
                "guestCount", 2, "notes", "L4-cp"), admin), String.class);
        assertThat(post.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return om.readTree(post.getBody()).get("id").asText();
    }

    private void cancel(String admin, String id, String uid) {
        restTemplate.exchange(url("/api/reservations/" + id + "/status"), HttpMethod.PATCH,
            jsonJwtEntity(Map.of("status", "cancelled", "changedById", uid, "reason", "L4-cp cleanup"), admin),
            String.class);
    }

    @Test
    void proposeThenAccept_appliesProposedSlot_andNotifiesClient() throws Exception {
        String admin = adminBearer();
        String[] rt = restoTenant();
        String restaurantId = rt[0], tenantId = rt[1], uid = userId();
        String id = createPending(admin, tenantId, uid, restaurantId);

        Instant proposed = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        // ── 1) Restaurant propose un nouveau créneau ──────────────────────────
        Map<String, Object> proposeBody = new HashMap<>();
        proposeBody.put("status", "counter_proposed");
        proposeBody.put("changedById", uid);
        proposeBody.put("reason", "autre créneau svp");
        proposeBody.put("proposedReservationAt", proposed.toString());
        ResponseEntity<String> propose = restTemplate.exchange(url("/api/reservations/" + id + "/status"),
            HttpMethod.PATCH, jsonJwtEntity(proposeBody, admin), String.class);
        assertThat(propose.getStatusCode()).isEqualTo(HttpStatus.OK);
        var proposeJson = om.readTree(propose.getBody());
        assertThat(proposeJson.get("status").asText()).isEqualTo("counter_proposed");
        assertThat(Instant.parse(proposeJson.get("proposedReservationAt").asText())).isEqualTo(proposed);

        // ── 2) Le client est notifié (event async after-commit → on poll) ─────
        boolean notified = false;
        for (int i = 0; i < 40 && !notified; i++) {
            Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE recipient_user_id = ?::uuid "
                + "AND type = 'reservation' AND title = 'Nouvelle proposition' "
                + "AND created_at > NOW() - INTERVAL '2 minutes'",
                Integer.class, uid);
            notified = n != null && n > 0;
            if (!notified) Thread.sleep(100);
        }
        assertThat(notified).as("notification 'Nouvelle proposition' créée pour le client").isTrue();

        // ── 3) Le client accepte : le créneau proposé devient effectif ────────
        ResponseEntity<String> accept = restTemplate.exchange(url("/api/reservations/" + id + "/status"),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("status", "confirmed", "changedById", uid, "reason", "ok"), admin),
            String.class);
        assertThat(accept.getStatusCode()).isEqualTo(HttpStatus.OK);
        var acceptJson = om.readTree(accept.getBody());
        assertThat(acceptJson.get("status").asText()).isEqualTo("confirmed");
        assertThat(acceptJson.get("proposedReservationAt").isNull()).as("proposition consommée").isTrue();
        assertThat(Instant.parse(acceptJson.get("reservationAt").asText()).truncatedTo(ChronoUnit.SECONDS))
            .as("le créneau proposé devient reservation_at").isEqualTo(proposed);

        cancel(admin, id, uid);
    }

    @Test
    void propose_missingProposedSlot_returns400() throws Exception {
        String admin = adminBearer();
        String[] rt = restoTenant();
        String id = createPending(admin, rt[1], userId(), rt[0]);

        ResponseEntity<String> res = restTemplate.exchange(url("/api/reservations/" + id + "/status"),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("status", "counter_proposed", "reason", "sans créneau"), admin),
            String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        cancel(admin, id, userId());
    }

    @Test
    void propose_pastProposedSlot_returns400() throws Exception {
        String admin = adminBearer();
        String[] rt = restoTenant();
        String id = createPending(admin, rt[1], userId(), rt[0]);

        Map<String, Object> body = new HashMap<>();
        body.put("status", "counter_proposed");
        body.put("proposedReservationAt", Instant.now().minus(1, ChronoUnit.HOURS).toString());
        ResponseEntity<String> res = restTemplate.exchange(url("/api/reservations/" + id + "/status"),
            HttpMethod.PATCH, jsonJwtEntity(body, admin), String.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        cancel(admin, id, userId());
    }

    @Test
    void proposeThenDecline_clearsProposedSlot() throws Exception {
        String admin = adminBearer();
        String[] rt = restoTenant();
        String uid = userId();
        String id = createPending(admin, rt[1], uid, rt[0]);

        Map<String, Object> proposeBody = new HashMap<>();
        proposeBody.put("status", "counter_proposed");
        proposeBody.put("proposedReservationAt", Instant.now().plus(4, ChronoUnit.DAYS).toString());
        assertThat(restTemplate.exchange(url("/api/reservations/" + id + "/status"), HttpMethod.PATCH,
            jsonJwtEntity(proposeBody, admin), String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Le client refuse → cancelled, et la proposition est nettoyée.
        ResponseEntity<String> decline = restTemplate.exchange(url("/api/reservations/" + id + "/status"),
            HttpMethod.PATCH, jsonJwtEntity(Map.of("status", "cancelled", "changedById", uid, "reason", "ne convient pas"), admin),
            String.class);
        assertThat(decline.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(om.readTree(decline.getBody()).get("status").asText()).isEqualTo("cancelled");

        String dbProposed = jdbc.queryForObject(
            "SELECT proposed_reservation_at::text FROM reservations WHERE id = ?::uuid", String.class, id);
        assertThat(dbProposed).as("proposed_reservation_at nettoyé après refus").isNull();
    }

    @Test
    void statusChange_noBearer_401() {
        assertThat(restTemplate.exchange(url("/api/reservations/" + UUID.randomUUID() + "/status"),
            HttpMethod.PATCH, jwtEntity(null), String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Expiry (V89) : le cron {@code expireUnansweredReservations} annule une
     * contre-proposition dont le créneau proposé est à &lt; H-2 (client n'a pas
     * répondu) et laisse intacte celle dont le créneau est lointain.
     */
    @Test
    void expiryCron_cancelsImminentCounterProposal_keepsFarOne() throws Exception {
        String admin = adminBearer();
        String[] rt = restoTenant();
        String uid = userId();

        // imminent : créneau proposé dans 1h → doit être annulé par le cron (H-2).
        String imminent = createPending(admin, rt[1], uid, rt[0]);
        jdbc.update("UPDATE reservations SET status = 'counter_proposed', "
            + "proposed_reservation_at = NOW() + INTERVAL '1 hour' WHERE id = ?::uuid", imminent);
        // lointain : créneau proposé dans 5j → doit rester counter_proposed.
        String far = createPending(admin, rt[1], uid, rt[0]);
        jdbc.update("UPDATE reservations SET status = 'counter_proposed', "
            + "proposed_reservation_at = NOW() + INTERVAL '5 days' WHERE id = ?::uuid", far);

        cron.expireUnansweredReservations();

        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?::uuid", String.class, imminent))
            .as("contre-proposition imminente (H-2) annulée").isEqualTo("cancelled");
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?::uuid", String.class, far))
            .as("contre-proposition lointaine conservée").isEqualTo("counter_proposed");

        cancel(admin, far, uid);
    }
}
