package com.onesley.oneclick.modules.resource_booking;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.modules.resource_booking.internal.ResourceBookingCronJobs;
import com.onesley.oneclick.modules.resource_booking.internal.ResourceBookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Intégration gaps #4/#5 — les crons booking PCC (auto-cancel notifiant + rappels J-1/H-2)
 * exécutent leur SQL natif contre le schéma réel sans exception ; et la requête de résolution
 * staff-par-tenant (#3) renvoie le staff actif en excluant l'organisateur.
 *
 * <p>FCM est en mode stub en test ({@code app.fcm.*} vides) — on valide la chaîne backend
 * (cron → event → listener → notif/SQL), pas le push réel.
 */
class ResourceBookingReminderCronIntegrationTest extends AbstractIntegrationTest {

    @Autowired ResourceBookingCronJobs cron;
    @Autowired ResourceBookingRepository bookingRepository;

    @Test
    void crons_executeAgainstRealSchema_noException() {
        assertThatCode(() -> {
            cron.sendResourceBookingRemindersJ1();
            cron.sendResourceBookingRemindersH2();
            cron.expireUnansweredResourceBookings();
        }).doesNotThrowAnyException();
    }

    @Test
    void expire_seededPendingBooking_cancelsAndNotifiesOrganizer() throws Exception {
        // Une ressource seedée (+ son tenant) ; sinon skip proprement (DB de test minimale).
        List<Map<String, Object>> resources =
            jdbc.queryForList("SELECT id, tenant_id FROM resources WHERE deleted_at IS NULL LIMIT 1");
        assumeThat(resources).as("au moins une ressource seedée").isNotEmpty();
        UUID resourceId = UUID.fromString(resources.get(0).get("id").toString());
        UUID organizer = SEED_SUPERADMIN_ID;
        UUID bookingId = UUID.randomUUID();

        // pending, créneau dans 1h (≤ NOW()+2h → éligible auto-cancel).
        jdbc.update("""
            INSERT INTO resource_bookings (id, resource_id, organizer_id, start_at, end_at, status)
            VALUES (?, ?, ?, NOW() + INTERVAL '1 hour', NOW() + INTERVAL '2 hours', 'pending')
            """, bookingId, resourceId, organizer);

        try {
            cron.expireUnansweredResourceBookings();
            String status = jdbc.queryForObject(
                "SELECT status FROM resource_bookings WHERE id = ?", String.class, bookingId);
            assertThat(status).as("booking pending non répondu annulé").isEqualTo("cancelled");

            boolean found = false;
            for (int i = 0; i < 40 && !found; i++) {
                Long n = jdbc.queryForObject("""
                    SELECT count(*) FROM notifications
                    WHERE recipient_user_id = ? AND type = 'reservation' AND title = 'Réservation annulée'
                    """, Long.class, organizer);
                if (n != null && n >= 1) { found = true; break; }
                Thread.sleep(200);
            }
            assertThat(found).as("notif d'annulation booking créée pour l'organisateur (event → handler)").isTrue();
        } finally {
            jdbc.update("DELETE FROM notifications WHERE recipient_user_id = ? AND title = 'Réservation annulée'", organizer);
            jdbc.update("DELETE FROM resource_bookings WHERE id = ?", bookingId);
        }
    }

    @Test
    void findStaffRecipientsForTenant_returnsActiveStaff_excludesOrganizer() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id AS restaurant_id, r.tenant_id FROM restaurants r WHERE r.tenant_id IS NOT NULL LIMIT 1");
        assumeThat(rows).as("au moins un restaurant seedé avec tenant").isNotEmpty();
        UUID restaurantId = UUID.fromString(rows.get(0).get("restaurant_id").toString());
        UUID tenantId = UUID.fromString(rows.get(0).get("tenant_id").toString());
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
            assertThat(bookingRepository.findStaffRecipientIdsForTenant(tenantId, UUID.randomUUID()))
                .as("staff actif du tenant destinataire").contains(staff);
            assertThat(bookingRepository.findStaffRecipientIdsForTenant(tenantId, staff))
                .as("organisateur exclu même s'il est staff").doesNotContain(staff);
        } finally {
            if (inserted) {
                jdbc.update("DELETE FROM restaurant_staffs WHERE restaurant_id = ? AND user_id = ?", restaurantId, staff);
            }
        }
    }
}
