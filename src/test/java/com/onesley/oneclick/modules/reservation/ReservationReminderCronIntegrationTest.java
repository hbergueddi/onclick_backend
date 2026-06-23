package com.onesley.oneclick.modules.reservation;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.modules.reservation.internal.ReservationCronJobs;
import com.onesley.oneclick.modules.reservation.internal.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Intégration R1 — les crons de rappels exécutent leur SQL natif contre le schéma réel
 * <b>et</b> déclenchent le flux event → listener → notif in-app (avec metadata pour l'anti-doublon).
 *
 * <p>FCM est en mode stub dans le profil de test ({@code app.fcm.*} vides), donc le push réel
 * n'est pas émis — on valide la chaîne backend (cron → {@code ReservationReminderDueEvent} →
 * {@code NotificationEventHandler} → {@code NotificationService.createReservationReminder}).
 */
class ReservationReminderCronIntegrationTest extends AbstractIntegrationTest {

    @Autowired ReservationCronJobs cron;
    @Autowired ReservationRepository reservationRepository;

    @Test
    void crons_executeAgainstRealSchema_noException() {
        // Valide les requêtes natives (SELECT rappels J-1/H-2 + UPDATE auto-cancel) sur le schéma réel.
        assertThatCode(() -> {
            cron.sendReservationRemindersJ1();
            cron.sendReservationRemindersH2();
            cron.expireUnansweredReservations();
        }).doesNotThrowAnyException();
    }

    @Test
    void remindersJ1_seededConfirmedReservationTomorrow_createsReminderNotifWithMetadata() throws Exception {
        // Récupère un resto seedé (+ son tenant) ; sinon skip proprement (DB de test minimale).
        List<Map<String, Object>> restos =
            jdbc.queryForList("SELECT id, tenant_id FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1");
        assumeThat(restos).as("au moins un restaurant seedé avec tenant").isNotEmpty();
        UUID restaurantId = UUID.fromString(restos.get(0).get("id").toString());
        UUID tenantId = UUID.fromString(restos.get(0).get("tenant_id").toString());
        UUID client = SEED_SUPERADMIN_ID;
        UUID resaId = UUID.randomUUID();

        jdbc.update("""
            INSERT INTO reservations (id, tenant_id, client_id, restaurant_id, reservation_at, guest_count, status)
            VALUES (?, ?, ?, ?, (CURRENT_DATE + INTERVAL '1 day') + INTERVAL '20 hours', 2, 'confirmed')
            """, resaId, tenantId, client, restaurantId);

        try {
            cron.sendReservationRemindersJ1();
            // Le listener @ApplicationModuleListener (after-commit) crée la notif avec metadata slot=j1.
            boolean found = false;
            for (int i = 0; i < 40 && !found; i++) {
                Long n = jdbc.queryForObject("""
                    SELECT count(*) FROM notifications
                    WHERE recipient_user_id = ? AND type = 'reservation'
                      AND metadata->>'reservationId' = ? AND metadata->>'slot' = 'j1'
                    """, Long.class, client, resaId.toString());
                if (n != null && n >= 1) { found = true; break; }
                Thread.sleep(200);
            }
            assertThat(found).as("notif rappel j1 créée par le listener (event → handler → service)").isTrue();
        } finally {
            jdbc.update("DELETE FROM notifications WHERE recipient_user_id = ? AND metadata->>'reservationId' = ?",
                client, resaId.toString());
            jdbc.update("DELETE FROM reservations WHERE id = ?", resaId);
        }
    }

    /**
     * Gap #1 — une résa {@code pending} non répondue à H-2 est auto-annulée ET le client reçoit
     * une notif d'annulation (avant : UPDATE muet, 0 notif). Valide cron → event → handler → notif.
     */
    @Test
    void expireUnanswered_seededPending_cancelsAndNotifiesClient() throws Exception {
        List<Map<String, Object>> restos =
            jdbc.queryForList("SELECT id, tenant_id FROM restaurants WHERE tenant_id IS NOT NULL LIMIT 1");
        assumeThat(restos).as("au moins un restaurant seedé avec tenant").isNotEmpty();
        UUID restaurantId = UUID.fromString(restos.get(0).get("id").toString());
        UUID tenantId = UUID.fromString(restos.get(0).get("tenant_id").toString());
        UUID client = SEED_SUPERADMIN_ID;
        UUID resaId = UUID.randomUUID();

        // pending, créneau dans 1h (≤ NOW()+2h → éligible à l'auto-cancel).
        jdbc.update("""
            INSERT INTO reservations (id, tenant_id, client_id, restaurant_id, reservation_at, guest_count, status)
            VALUES (?, ?, ?, ?, NOW() + INTERVAL '1 hour', 2, 'pending')
            """, resaId, tenantId, client, restaurantId);

        try {
            cron.expireUnansweredReservations();
            String status = jdbc.queryForObject("SELECT status FROM reservations WHERE id = ?", String.class, resaId);
            assertThat(status).as("la résa pending non répondue est annulée").isEqualTo("cancelled");

            boolean found = false;
            for (int i = 0; i < 40 && !found; i++) {
                Long n = jdbc.queryForObject("""
                    SELECT count(*) FROM notifications
                    WHERE recipient_user_id = ? AND type = 'reservation' AND title = 'Réservation expirée'
                    """, Long.class, client);
                if (n != null && n >= 1) { found = true; break; }
                Thread.sleep(200);
            }
            assertThat(found).as("notif d'annulation auto créée pour le client (event → handler)").isTrue();
        } finally {
            jdbc.update("DELETE FROM notifications WHERE recipient_user_id = ? AND title = 'Réservation expirée'", client);
            jdbc.update("DELETE FROM reservations WHERE id = ?", resaId);
        }
    }

    /**
     * Gap #2 — la requête de résolution des destinataires staff renvoie le staff actif du resto
     * et exclut le client demandeur (qui pourrait par ailleurs être staff).
     */
    @Test
    void findStaffRecipients_returnsActiveStaff_excludesClient() {
        List<Map<String, Object>> restos = jdbc.queryForList("SELECT id FROM restaurants LIMIT 1");
        assumeThat(restos).as("au moins un restaurant seedé").isNotEmpty();
        UUID restaurantId = UUID.fromString(restos.get(0).get("id").toString());
        UUID staff = SEED_SUPERADMIN_ID;

        Long pre = jdbc.queryForObject(
            "SELECT count(*) FROM restaurant_staffs WHERE restaurant_id = ? AND user_id = ?",
            Long.class, restaurantId, staff);
        boolean inserted = (pre == null || pre == 0);
        if (inserted) {
            jdbc.update("INSERT INTO restaurant_staffs (id, restaurant_id, user_id, role_code) VALUES (?, ?, ?, 'owner')",
                UUID.randomUUID(), restaurantId, staff);
        }
        try {
            assertThat(reservationRepository.findStaffRecipientIdsForRestaurant(restaurantId, UUID.randomUUID()))
                .as("le staff actif est destinataire").contains(staff);
            assertThat(reservationRepository.findStaffRecipientIdsForRestaurant(restaurantId, staff))
                .as("le client demandeur est exclu même s'il est staff").doesNotContain(staff);
        } finally {
            if (inserted) {
                jdbc.update("DELETE FROM restaurant_staffs WHERE restaurant_id = ? AND user_id = ?", restaurantId, staff);
            }
        }
    }
}
