package com.onesley.oneclick.dto.loyalty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DTO pour {@code scanned_tickets} (généré par scripts/scaffold-jpa.mjs).
 */
public record ScannedTicketDto(
    UUID id,
    UUID restaurantId,
    UUID clientId,
    UUID scannedBy,
    String ticketRef,
    BigDecimal montant,
    Integer pointsCredites,
    Map<String, Object> items,
    String status,
    String photoUrl,
    Instant createdAt,
    UUID reservationId,
    UUID createdBy,
    UUID modifiedBy
) {
}
