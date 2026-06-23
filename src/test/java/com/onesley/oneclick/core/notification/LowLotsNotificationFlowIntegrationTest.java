package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.shared.events.NoShowPenaltyFinalizedEvent;
import com.onesley.oneclick.shared.events.OfferExpiredEvent;
import com.onesley.oneclick.shared.events.PointsGiftedEvent;
import com.onesley.oneclick.shared.events.PromoRequestReviewedEvent;
import com.onesley.oneclick.shared.events.ReservationGuestRemovedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration event-driven (contexte Spring complet) des 5 lots LOW : la publication réelle d'un event
 * dans une transaction committée (listeners {@code @ApplicationModuleListener} after-commit + async)
 * persiste une ligne {@code notifications} pour le bon destinataire et le bon {@code type}.
 *
 * <ul>
 *   <li><b>B14</b> — {@code ReservationGuestRemovedEvent} → notif {@code reservation} à l'invité ;</li>
 *   <li><b>B12</b> — {@code PromoRequestReviewedEvent} → notif {@code promotion} au demandeur ;</li>
 *   <li><b>B11</b> — {@code OfferExpiredEvent} → notif {@code system} à chaque staff porté ;</li>
 *   <li><b>B7</b> — {@code PointsGiftedEvent} → notif {@code loyalty} au bénéficiaire ;</li>
 *   <li><b>B10</b> — {@code NoShowPenaltyFinalizedEvent} → notif {@code reservation} au client AVEC
 *       {@code metadata.kind = 'noshow_penalty_final'} (anti-doublon).</li>
 * </ul>
 *
 * <p>Destinataires = vrais users seedés (FK {@code notifications.recipient_user_id → users} + le
 * garde-fou {@code NotificationRecipientGuard} exigent un compte existant).
 */
class LowLotsNotificationFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired ApplicationEventPublisher events;

    private UUID anyUser() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class));
    }

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

    // ── B14 ────────────────────────────────────────────────────────────────────
    @Test
    void guestRemoved_persistsReservationNotifForInvited() throws Exception {
        UUID invited = anyUser();
        Instant since = Instant.now();
        try {
            publishCommitted(new ReservationGuestRemovedEvent(UUID.randomUUID(), invited, "Adil"));
            assertThat(awaitNotif(invited, "reservation", since))
                .as("B14 : notif reservation persistée pour l'invité").isTrue();
        } finally {
            cleanup(invited, "reservation", since);
        }
    }

    // ── B12 ────────────────────────────────────────────────────────────────────
    @Test
    void promoReviewed_persistsPromotionNotifForRequester() throws Exception {
        UUID requester = anyUser();
        Instant since = Instant.now();
        try {
            publishCommitted(new PromoRequestReviewedEvent(
                UUID.randomUUID(), requester, false, "Promo X", "hors charte", Instant.now()));
            assertThat(awaitNotif(requester, "promotion", since))
                .as("B12 : notif promotion persistée pour le demandeur").isTrue();
        } finally {
            cleanup(requester, "promotion", since);
        }
    }

    // ── B11 ────────────────────────────────────────────────────────────────────
    @Test
    void offerExpired_persistsSystemNotifForStaff() throws Exception {
        UUID staff = anyUser();
        Instant since = Instant.now();
        try {
            publishCommitted(new OfferExpiredEvent(
                UUID.randomUUID(), UUID.randomUUID(), List.of(staff), "Brunch -20%", Instant.now()));
            assertThat(awaitNotif(staff, "system", since))
                .as("B11 : notif system persistée pour le staff").isTrue();
        } finally {
            cleanup(staff, "system", since);
        }
    }

    // ── B7 ─────────────────────────────────────────────────────────────────────
    @Test
    void pointsGifted_persistsLoyaltyNotifForReceiver() throws Exception {
        UUID receiver = anyUser();
        Instant since = Instant.now();
        try {
            publishCommitted(new PointsGiftedEvent(
                UUID.randomUUID(), receiver, 30, "Sara", Instant.now()));
            assertThat(awaitNotif(receiver, "loyalty", since))
                .as("B7 : notif loyalty persistée pour le bénéficiaire").isTrue();
        } finally {
            cleanup(receiver, "loyalty", since);
        }
    }

    // ── B10 ────────────────────────────────────────────────────────────────────
    @Test
    void noShowPenaltyFinalized_persistsReservationNotifWithMetadataMarker() throws Exception {
        UUID client = anyUser();
        UUID resa = UUID.randomUUID();
        Instant since = Instant.now();
        try {
            publishCommitted(new NoShowPenaltyFinalizedEvent(client, resa, Instant.now()));
            assertThat(awaitNotif(client, "reservation", since))
                .as("B10 : notif reservation persistée pour le client").isTrue();
            // marqueur metadata anti-doublon présent (kind + reservationId)
            Long marked = jdbc.queryForObject("""
                    SELECT count(*) FROM notifications
                    WHERE recipient_user_id = ?::uuid
                      AND created_at >= ?
                      AND metadata->>'kind' = 'noshow_penalty_final'
                      AND metadata->>'reservationId' = ?
                    """, Long.class, client.toString(), java.sql.Timestamp.from(since), resa.toString());
            assertThat(marked).as("B10 : metadata anti-doublon écrite").isNotNull().isGreaterThanOrEqualTo(1L);
        } finally {
            cleanup(client, "reservation", since);
        }
    }
}
