package com.onesley.oneclick.dto.restaurant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code restaurant_tier_config} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantTierConfigDto(
    UUID id,
    String name,
    String slug,
    BigDecimal minCa,
    BigDecimal maxCa,
    Integer gracePeriodMonths,
    String color,
    String icon,
    Integer position,
    String description,
    Instant createdAt,
    Instant updatedAt
) {
}
