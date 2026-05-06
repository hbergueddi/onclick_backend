package com.onesley.oneclick.dto.loyalty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code tier_thresholds} (généré par scripts/scaffold-jpa.mjs).
 */
public record TierThresholdDto(
    UUID id,
    String tierName,
    Integer periodDays,
    BigDecimal minSpend,
    Integer pointsRequired,
    Integer sortOrder,
    String benefits,
    Boolean enabled,
    Instant createdAt,
    Instant updatedAt,
    BigDecimal gainBonusPct,
    BigDecimal pointValueMadOverride,
    BigDecimal minTicketOverride,
    Integer maxPointsPerTicketOverride,
    Integer benefitDurationDaysOverride,
    Integer maxRedemptionPer24hOverride,
    BigDecimal maxRedemptionRatioPctOverride,
    Integer otpRequiredAbovePtsOverride,
    BigDecimal otpRequiredAboveRatioPctOverride,
    BigDecimal tauxConversionOverride
) {
}
