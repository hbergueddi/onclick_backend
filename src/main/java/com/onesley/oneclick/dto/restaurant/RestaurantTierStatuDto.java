package com.onesley.oneclick.dto.restaurant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code restaurant_tier_status} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantTierStatuDto(
    UUID id,
    UUID restaurantId,
    UUID currentTierId,
    String currentTierSlug,
    BigDecimal monthlyCa,
    Instant tierAchievedAt,
    Instant graceExpiresAt,
    Instant lastEvaluatedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
