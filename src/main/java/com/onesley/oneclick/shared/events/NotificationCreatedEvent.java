package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand une notification in-app est créée ({@code NotificationService.create}).
 *
 * <p>Consommé par {@code UserRealtimePublisher} (package {@code realtime}) qui pousse un signal
 * STOMP sur la queue privée du destinataire ({@code /user/queue/notifications}) → la cloche
 * front se rafraîchit en TEMPS RÉEL (directive : pas de polling). Pattern event server-side :
 * {@code core.notification} (CLOSED, deps {audit,exception,security,shared}) ne dépend pas de
 * l'infra {@code realtime} ; il publie l'event (shared) et {@code realtime} (OPEN) l'écoute.</p>
 *
 * @param recipientUserId destinataire (peut être null pour une notif broadcast → pas de push ciblé)
 * @param notificationId  id de la notification créée
 * @param type            type métier (resa, points, promo, parrainage, …)
 * @param occurredAt      horodatage de création
 */
public record NotificationCreatedEvent(
    UUID recipientUserId,
    UUID notificationId,
    String type,
    Instant occurredAt
) {
}
