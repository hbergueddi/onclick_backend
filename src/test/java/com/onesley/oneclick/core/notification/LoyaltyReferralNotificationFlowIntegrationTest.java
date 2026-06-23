package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.shared.events.LoyaltyEarnedEvent;
import com.onesley.oneclick.shared.events.RestaurantReferralActivatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration event-driven (contexte Spring complet) des Lots B2 / B3 : la publication réelle d'un
 * event via {@link ApplicationEventPublisher} (dans une transaction committée — les listeners sont
 * {@code @ApplicationModuleListener} = after-commit + async) provoque la persistance d'une ligne
 * {@code notifications} pour le bon destinataire, avec le bon {@code type}.
 *
 * <ul>
 *   <li><b>B3</b> — {@code RestaurantReferralActivatedEvent} → notif {@code system} à chaque admin
 *       porté sur l'event ;</li>
 *   <li><b>B2</b> — {@code LoyaltyEarnedEvent(reason="snap2earn|…")} → notif {@code loyalty} au client ;
 *       {@code reason="RESTAURANT_REFERRAL"} → AUCUNE notif (filtre conservateur).</li>
 * </ul>
 *
 * <p>Destinataires = vrais users seedés (FK {@code notifications.recipient_user_id → users} + le
 * garde-fou {@code NotificationRecipientGuard} exigent un compte existant).
 */
class LoyaltyReferralNotificationFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired ApplicationEventPublisher events;

    /** Un user actif quelconque (destinataire valide pour la FK + le garde-fou). */
    private UUID anyUser() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class));
    }

    /** Publie l'event dans une transaction NEUVE committée → déclenche les listeners after-commit. */
    private void publishCommitted(Object event) {
        new TransactionTemplate(txManager).executeWithoutResult(s -> events.publishEvent(event));
    }

    /** Attend (polling) qu'une notif du type donné existe pour ce destinataire, créée depuis {@code since}. */
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

    @Test
    void restaurantReferralActivated_persistsSystemNotifForEachAdmin() throws Exception {
        UUID admin = anyUser();
        Instant since = Instant.now();
        try {
            publishCommitted(new RestaurantReferralActivatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100, UUID.randomUUID(),
                List.of(admin), Instant.now()));

            assertThat(awaitNotif(admin, "system", since))
                .as("B3 : notif system persistée pour l'admin").isTrue();
        } finally {
            jdbc.update("DELETE FROM notifications WHERE recipient_user_id = ?::uuid AND type = 'system' AND created_at >= ?",
                admin.toString(), java.sql.Timestamp.from(since));
        }
    }

    @Test
    void loyaltyEarned_snap2earn_persistsLoyaltyNotifForClient() throws Exception {
        UUID client = anyUser();
        Instant since = Instant.now();
        try {
            publishCommitted(new LoyaltyEarnedEvent(
                UUID.randomUUID(), UUID.randomUUID(), client, UUID.randomUUID(), UUID.randomUUID(),
                25, new BigDecimal("250.00"), "snap2earn|TICKET-IT|", Instant.now()));

            assertThat(awaitNotif(client, "loyalty", since))
                .as("B2 : notif loyalty persistée pour le client (scan)").isTrue();
        } finally {
            jdbc.update("DELETE FROM notifications WHERE recipient_user_id = ?::uuid AND type = 'loyalty' AND created_at >= ?",
                client.toString(), java.sql.Timestamp.from(since));
        }
    }

    @Test
    void loyaltyEarned_referralReason_persistsNoNotif() throws Exception {
        UUID client = anyUser();
        Instant since = Instant.now();
        try {
            publishCommitted(new LoyaltyEarnedEvent(
                UUID.randomUUID(), UUID.randomUUID(), client, UUID.randomUUID(), UUID.randomUUID(),
                100, null, "RESTAURANT_REFERRAL", Instant.now()));

            // Laisse le temps à un éventuel listener async de fire, puis vérifie l'ABSENCE.
            Thread.sleep(1200);
            assertThat(countNotifsSince(client, "loyalty", since))
                .as("B2 : crédit non-scan (referral) → pas de notif « points gagnés »").isZero();
        } finally {
            jdbc.update("DELETE FROM notifications WHERE recipient_user_id = ?::uuid AND type = 'loyalty' AND created_at >= ?",
                client.toString(), java.sql.Timestamp.from(since));
        }
    }
}
