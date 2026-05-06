package com.onesley.oneclick.dto.loyalty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO read-only pour {@code v_client_loyalty_summary} (généré par scripts/scaffold-jpa.mjs).
 */
public record ClientLoyaltySummaryViewDto(
    UUID clientId,
    String firstName,
    String lastName,
    String city,
    String phone,
    Instant createdAt,
    Long totalPointsEarned,
    Long availablePoints,
    BigDecimal totalSpent,
    Long restaurantsVisited,
    Long totalReservations,
    Long reservationsHonorees,
    Long reservationsNoShow,
    Long ticketsScanned
) {
}
