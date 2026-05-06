package com.onesley.oneclick.dto.loyalty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code gain_rules} (généré par scripts/scaffold-jpa.mjs).
 */
public record GainRuleDto(
    UUID id,
    String name,
    String description,
    String type,
    BigDecimal tauxConversion,
    BigDecimal minTicket,
    Boolean enabled,
    Instant createdAt,
    Instant updatedAt,
    Integer maxPointsParTicket,
    String periodType,
    Integer periodValue,
    Integer benefitDurationDays,
    BigDecimal minSpendMonthly,
    BigDecimal pointValueMad,
    Integer maxRedemptionPer24h,
    BigDecimal maxRedemptionRatioPct,
    Integer otpRequiredAbovePts,
    BigDecimal otpRequiredAboveRatioPct,
    UUID tenantId
) {
}
