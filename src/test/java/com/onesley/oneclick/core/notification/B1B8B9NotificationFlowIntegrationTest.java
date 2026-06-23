package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.shared.events.NoShowDisputeCreatedEvent;
import com.onesley.oneclick.shared.events.ReferralActivatedEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
import com.onesley.oneclick.shared.events.ResourceBookingStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration event-driven (contexte Spring complet) des Lots B1 / B8 / B9 : la publication réelle
 * d'un event via {@link ApplicationEventPublisher} dans une transaction committée (les listeners
 * {@code @ApplicationModuleListener} sont after-commit + async) provoque la persistance d'une ligne
 * {@code notifications} pour le bon destinataire et le bon {@code type}.
 *
 * <ul>
 *   <li><b>B1</b> — {@code ReferralActivatedEvent} → notif {@code loyalty} au parrain ET au filleul ;</li>
 *   <li><b>B8</b> — {@code NoShowDisputeCreatedEvent} → notif {@code reservation} à chaque staff porté ;</li>
 *   <li><b>B9 résa</b> — {@code ReservationStatusChangedEvent(cancelled, changedBy=client, staff=[..])}
 *       → notif {@code reservation} au staff ; constructeur compat (staff null) → AUCUNE notif staff ;</li>
 *   <li><b>B9 booking</b> — {@code ResourceBookingStatusChangedEvent(cancelled, changedBy=organizer,
 *       staff=[..])} → notif {@code reservation} au staff (auto-annulation membre).</li>
 * </ul>
 *
 * <p>Destinataires = vrais users seedés (FK {@code notifications.recipient_user_id → users} + le
 * garde-fou {@code NotificationRecipientGuard} exigent un compte existant).
 */
class B1B8B9NotificationFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired ApplicationEventPublisher events;

    private UUID anyUser() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class));
    }

    /** Deux users actifs distincts (parrain/filleul, ou client/staff). */
    private List<UUID> twoUsers() {
        List<String> ids = jdbc.queryForList(
            "SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 2", String.class);
        return ids.stream().map(UUID::fromString).toList();
    }

    private void publishCommitted(Object event) {
        new TransactionTemplate(txManager).executeWithoutResult(s -> events.publishEvent(event));
    }

    private long countNotifsSince(UUID recipient, String type, Instant since) {
        Long n = jdbc.queryForObject(
            "SELECT count(*) FROM notifications WHERE recipient_user_id = ?::uuid AND type = ? AND created_at >= ?",
            Long.class, recipient.toString(), type, java.sql.Timestamp.from(since));
        return n == null ? 0 : n;
    }

    private boolean awaitNotif(UUID recipient, String type, Instant since) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (countNotifsSince(recipient, type, since) >= 1) return true;
            Thread.sleep(200);
        }
        return false;
    }

    private void cleanup(UUID recipient, String type, Instant since) {
        jdbc.update("DELETE FROM notifications WHERE recipient_user_id = ?::uuid AND type = ? AND created_at >= ?",
            recipient.toString(), type, java.sql.Timestamp.from(since));
    }

    // ── B1 ───────────────────────────────────────────────────────────────────
    @Test
    void referralActivated_persistsLoyaltyNotifForReferrerAndReferred() throws Exception {
        List<UUID> users = twoUsers();
        UUID referrer = users.get(0), referred = users.get(1);
        Instant since = Instant.now();
        try {
            publishCommitted(new ReferralActivatedEvent(
                UUID.randomUUID(), referrer, referred, 50, Instant.now()));

            assertThat(awaitNotif(referrer, "loyalty", since))
                .as("B1 : notif loyalty persistée pour le parrain").isTrue();
            assertThat(awaitNotif(referred, "loyalty", since))
                .as("B1 : notif loyalty persistée pour le filleul").isTrue();
        } finally {
            cleanup(referrer, "loyalty", since);
            cleanup(referred, "loyalty", since);
        }
    }

    // ── B8 ───────────────────────────────────────────────────────────────────
    @Test
    void disputeCreated_persistsReservationNotifForEachStaff() throws Exception {
        UUID staff = anyUser();
        Instant since = Instant.now();
        try {
            publishCommitted(new NoShowDisputeCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(staff), "Sara K.", Instant.now()));

            assertThat(awaitNotif(staff, "reservation", since))
                .as("B8 : notif reservation persistée pour le staff").isTrue();
        } finally {
            cleanup(staff, "reservation", since);
        }
    }

    // ── B9 résa : annulation par le client → staff notifié ─────────────────────
    @Test
    void reservationCancelledByClient_persistsStaffNotif() throws Exception {
        List<UUID> users = twoUsers();
        UUID client = users.get(0), staff = users.get(1);
        Instant since = Instant.now();
        try {
            publishCommitted(new ReservationStatusChangedEvent(
                UUID.randomUUID(), client, UUID.randomUUID(), UUID.randomUUID(),
                "confirmed", "cancelled", null, client, List.of(staff), Instant.now()));

            assertThat(awaitNotif(staff, "reservation", since))
                .as("B9 : notif reservation persistée pour le staff (annulation client)").isTrue();
        } finally {
            cleanup(staff, "reservation", since);
            cleanup(client, "reservation", since);
        }
    }

    // ── B9 résa : annulation par le staff (compat constructor) → pas de notif staff ─
    @Test
    void reservationCancelledByStaff_persistsNoStaffNotif() throws Exception {
        UUID staff = anyUser();
        Instant since = Instant.now();
        try {
            // constructeur compat (pas de staffRecipientIds) → branche staff jamais déclenchée
            publishCommitted(new ReservationStatusChangedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "confirmed", "cancelled", null, Instant.now()));

            Thread.sleep(1200);
            assertThat(countNotifsSince(staff, "reservation", since))
                .as("B9 : annulation staff/système → aucune notif staff parasite").isZero();
        } finally {
            cleanup(staff, "reservation", since);
        }
    }

    // ── B9 booking : auto-annulation du membre → staff notifié ──────────────────
    @Test
    void bookingCancelledByMember_persistsStaffNotif() throws Exception {
        List<UUID> users = twoUsers();
        UUID member = users.get(0), staff = users.get(1);
        Instant since = Instant.now();
        try {
            publishCommitted(new ResourceBookingStatusChangedEvent(
                UUID.randomUUID(), member, UUID.randomUUID(), UUID.randomUUID(), "padel",
                "confirmed", "cancelled", member, List.of(staff), Instant.now()));

            assertThat(awaitNotif(staff, "reservation", since))
                .as("B9 : notif reservation persistée pour le staff (auto-annulation membre)").isTrue();
        } finally {
            cleanup(staff, "reservation", since);
            cleanup(member, "reservation", since);
        }
    }
}
