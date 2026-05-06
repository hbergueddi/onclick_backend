package com.onesley.oneclick.dto.loyalty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code gain_rule_requests} (généré par scripts/scaffold-jpa.mjs).
 */
public record GainRuleRequestDto(
    UUID id,
    UUID restaurantId,
    UUID requestedBy,
    String name,
    String description,
    String type,
    BigDecimal tauxConversion,
    BigDecimal minTicket,
    Integer maxPointsParTicket,
    String status,
    String rejectionReason,
    UUID reviewedBy,
    Instant reviewedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
