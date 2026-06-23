package com.onesley.oneclick.shared.events;


import java.time.Instant;
import java.util.UUID;

/**
 * Rappel d'une réservation de RESSOURCE PCC due (J-1 / H-2) — gap #4 : avant, aucun rappel n'était
 * envoyé pour les bookings PCC (le cron n'avait que l'expiration). Émis par
 * {@code ResourceBookingCronJobs}, consommé par {@code NotificationEventHandler.onResourceBookingReminderDue}
 * → notif in-app (metadata {@code {bookingId, slot}} pour l'anti-doublon du cron H-2) + push FCM.
 *
 * <p>Calque {@link ReservationReminderDueEvent} (rappels résa restaurant) — frontière Modulith :
 * {@code resource_booking} ne dépend pas de {@code core.notification}.
 */
public record ResourceBookingReminderDueEvent(
    UUID recipientUserId,
    UUID bookingId,
    String slot,
    String title,
    String body,
    String link,
    Instant occurredAt
) {
}
