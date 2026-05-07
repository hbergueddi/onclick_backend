package com.onesley.oneclick.dto.loyalty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code loyalty_points} (généré par scripts/scaffold-jpa.mjs).
 */
public record LoyaltyPointDto(
    UUID id,
    UUID clientId,
    UUID restaurantId,
    Integer points,
    String reason,
    Instant earnedAt,
    BigDecimal amountTtc,
    UUID creditedBy,
    Instant expiresAt,
    Integer remainingPoints,
    Instant notified7dAt,
    Instant notified1dAt,
    UUID createdBy,
    UUID modifiedBy
) {
}
