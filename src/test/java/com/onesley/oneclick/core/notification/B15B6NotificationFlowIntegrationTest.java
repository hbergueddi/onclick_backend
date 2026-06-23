package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.shared.events.AnnouncementPublishedEvent;
import com.onesley.oneclick.shared.events.RestaurantRestitutionPaidEvent;
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
 * Intégration event-driven (contexte Spring complet, {@code oneclick_enterprise}) des Lots
 * <b>B15a</b> et <b>B6</b> : la publication réelle d'un event after-commit provoque la persistance
 * d'une notification pour le bon destinataire et le bon {@code type}.
 *
 * <ul>
 *   <li><b>B15a</b> — {@code AnnouncementPublishedEvent} → notif {@code announcement} au staff porté ;</li>
 *   <li><b>B6</b> — {@code RestaurantRestitutionPaidEvent} → notif {@code system} (« Restitution
 *       versée 💸 ») au staff porté.</li>
 * </ul>
 *
 * <p>Le filtre de préférences staff (P1pref) défaut-ON : un destinataire sans row
 * {@code staff_notification_preferences} reçoit bien la notif (couvert ici par des users seedés
 * « vierges »). Destinataires = vrais users seedés (FK {@code notifications.recipient_user_id →
 * users} + garde {@code NotificationRecipientGuard}).
 */
class B15B6NotificationFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired ApplicationEventPublisher events;

    private UUID anyUser() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class));
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

    // ── B15a — annonce publiée → notif announcement au staff (défaut pref ON) ───
    @Test
    void announcementPublished_persistsAnnouncementNotifForStaff() throws Exception {
        UUID staff = anyUser();
        Instant since = Instant.now();
        try {
            publishCommitted(new AnnouncementPublishedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(staff), "Maintenance piscine", "permanent", Instant.now()));

            assertThat(awaitNotif(staff, "announcement", since))
                .as("B15a : notif announcement persistée pour le staff (push en plus, best-effort)").isTrue();
        } finally {
            cleanup(staff, "announcement", since);
        }
    }

    // ── B6 — restitution versée → notif system au staff ─────────────────────────
    @Test
    void restitutionPaid_persistsSystemNotifForStaff() throws Exception {
        UUID staff = anyUser();
        Instant since = Instant.now();
        try {
            publishCommitted(new RestaurantRestitutionPaidEvent(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("200.00"), List.of(staff), Instant.now()));

            assertThat(awaitNotif(staff, "system", since))
                .as("B6 : notif system (restitution versée) persistée pour le staff").isTrue();
        } finally {
            cleanup(staff, "system", since);
        }
    }
}
