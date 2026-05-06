package com.onesley.oneclick.dto.restaurant;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code restaurant_tables} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantTableDto(
    UUID id,
    UUID restaurantId,
    UUID zoneId,
    Integer numero,
    Integer capacite,
    String forme,
    String position,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
