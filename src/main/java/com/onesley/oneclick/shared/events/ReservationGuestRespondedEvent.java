package com.onesley.oneclick.shared.events;

import java.util.UUID;

/**
 * Event publié quand un invité répond à une invitation réservation (accepte / décline)
 * ({@code ReservationGuestService.updateStatus} → status accepted|refused).
 *
 * <p>Consommé par {@code NotificationEventHandler} (core/notification) qui notifie
 * l'ORGANISATEUR (client de la réservation) — server-side, car l'invité CLIENT n'a pas
 * {@code CREATE:NOTIFICATIONS} (le POST /api/notifications front 403'ait).</p>
 */
public record ReservationGuestRespondedEvent(
    UUID reservationId,
    UUID organizerId,
    boolean accepted
) {
}
