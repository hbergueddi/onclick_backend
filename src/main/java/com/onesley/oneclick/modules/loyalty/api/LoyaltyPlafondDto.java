package com.onesley.oneclick.modules.loyalty.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO public d'un plafond / limite Lounge.
 * Conversion Entity → DTO via {@code LoyaltyPlafond.toDto()}.
 */
public record LoyaltyPlafondDto(
    UUID id,
    String name,
    String scope,
    BigDecimal value,
    String unit,
    String description,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
}
