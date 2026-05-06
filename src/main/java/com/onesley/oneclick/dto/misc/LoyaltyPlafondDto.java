package com.onesley.oneclick.dto.misc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code loyalty_plafonds} (généré par scripts/scaffold-jpa.mjs).
 */
public record LoyaltyPlafondDto(
    UUID id,
    String name,
    String description,
    BigDecimal value,
    String unit,
    String scope,
    Boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
}
