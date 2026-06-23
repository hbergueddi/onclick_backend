package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié par {@code ReservationCronJobs} pour chaque réservation due d'un rappel
 * (J-1 la veille, H-2 deux heures avant).
 *
 * <p>Consommé par {@code NotificationEventHandler} → notification in-app (avec
 * {@code metadata = {reservationId, slot}} pour l'anti-doublon du cron H-2) + push FCM.
 *
 * <p>Sprint R1 (parité push legacy) : avant, les crons faisaient un pur {@code INSERT}
 * dans {@code notifications} sans jamais appeler {@code FcmPushService} — le
 * {@code channel='push'} du H-2 était trompeur (0 FCM réel).
 */
public record ReservationReminderDueEvent(
    UUID recipientUserId,
    UUID reservationId,
    String slot,        // "j1" | "h2"
    String title,
    String body,
    String link,
    Instant occurredAt
) {
}
