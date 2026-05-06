package com.onesley.oneclick.dto.restaurant;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO pour {@code restaurant_restitutions} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantRestitutionDto(
    UUID id,
    UUID restaurantId,
    LocalDate periodMonth,
    Integer totalPoints,
    String status,
    UUID processedBy,
    Instant processedAt,
    String notes,
    Instant createdAt,
    Instant updatedAt
) {
}
