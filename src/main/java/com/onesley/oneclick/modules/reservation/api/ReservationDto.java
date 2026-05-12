package com.onesley.oneclick.modules.reservation.api;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO public d'une réservation restaurant.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Reservation.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 */
public record ReservationDto(
    UUID id,
    UUID tenantId,
    UUID clientId,
    UUID restaurantId,
    UUID tableId,
    UUID serviceId,
    Instant reservationAt,
    Integer guestCount,
    String status,
    String notes,
    Instant createdAt
) {
}
