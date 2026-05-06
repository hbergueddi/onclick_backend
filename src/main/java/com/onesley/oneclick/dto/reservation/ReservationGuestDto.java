package com.onesley.oneclick.dto.reservation;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code reservation_guests} (généré par scripts/scaffold-jpa.mjs).
 */
public record ReservationGuestDto(
    UUID id,
    UUID reservationId,
    UUID invitedBy,
    String guestName,
    String guestPhone,
    UUID guestUserId,
    String status,
    Instant createdAt,
    Boolean seenByHost
) {
}
