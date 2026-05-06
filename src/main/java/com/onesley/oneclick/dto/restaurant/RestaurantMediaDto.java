package com.onesley.oneclick.dto.restaurant;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code restaurant_media} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantMediaDto(
    UUID id,
    UUID restaurantId,
    String type,
    String name,
    String url,
    String status,
    UUID uploadedBy,
    Instant createdAt,
    Instant updatedAt
) {
}
