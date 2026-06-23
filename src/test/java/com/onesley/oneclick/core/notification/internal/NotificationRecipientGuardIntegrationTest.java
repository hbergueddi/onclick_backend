package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intégration du garde-fou destinataire ({@link NotificationRecipientGuard}) contre la vraie table
 * {@code users}. Reproduit le bug : un destinataire absent (ex: event Spring Modulith dormant rejoué
 * après suppression du compte) ne doit plus violer la FK {@code notifications.recipient_user_id → users}
 * — {@code create()} saute l'insert et renvoie {@code null} (le listener async se complète donc
 * proprement → la publication d'event est marquée complète → plus de rejeu en boucle au restart).
 */
class NotificationRecipientGuardIntegrationTest extends AbstractIntegrationTest {

    @Autowired NotificationService notificationService;

    @Test
    void create_existingRecipient_persists() {
        UUID userId = UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM users WHERE deleted_at IS NULL LIMIT 1", String.class));

        var dto = notificationService.create(new NotificationCreateDto(
            userId, "system", "inapp", "Test garde-fou", "ok", null));

        assertThat(dto).isNotNull();
        assertThat(dto.recipientUserId()).isEqualTo(userId);
    }

    @Test
    void create_missingRecipient_skipsWithoutFkViolation() {
        UUID ghost = UUID.randomUUID(); // certainement absent de users

        var dto = notificationService.create(new NotificationCreateDto(
            ghost, "system", "inapp", "Test garde-fou", "ko", null));

        assertThat(dto).as("destinataire absent → notif ignorée (pas de violation FK)").isNull();
        Long rows = jdbc.queryForObject(
            "SELECT count(*) FROM notifications WHERE recipient_user_id = ?::uuid", Long.class, ghost.toString());
        assertThat(rows).isZero();
    }
}
