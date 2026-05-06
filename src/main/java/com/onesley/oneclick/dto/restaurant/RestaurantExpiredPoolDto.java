package com.onesley.oneclick.dto.restaurant;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code restaurant_expired_pool} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantExpiredPoolDto(
    UUID id,
    UUID restaurantId,
    UUID clientId,
    UUID expiredPointId,
    Integer points,
    Instant createdAt,
    Instant consolidatedAt,
    UUID restitutionId
) {
}
