package com.onesley.oneclick.dto.restaurant;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code restaurant_zones} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantZoneDto(
    UUID id,
    UUID restaurantId,
    String name,
    String type,
    String description,
    Integer capacite,
    Integer tablesCount,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
