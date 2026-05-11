package com.onesley.oneclick.shared.events;


import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand le statut d'une réservation change (confirmed/refused/cancelled/...).
 *
 * <p>Consommé par notification-service → notif au client.
 * Plus tard : loyalty-service pour ré-crédit auto si annulation no-show, etc.
 */
public record ReservationStatusChangedEvent(
    UUID reservationId,
    UUID clientId,
    UUID restaurantId,
    UUID tenantId,
    String oldStatus,
    String newStatus,
    String reason,
    Instant occurredAt
) {
}
