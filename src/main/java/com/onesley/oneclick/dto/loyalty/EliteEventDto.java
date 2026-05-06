package com.onesley.oneclick.dto.loyalty;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO pour {@code elite_events} (généré par scripts/scaffold-jpa.mjs).
 */
public record EliteEventDto(
    UUID id,
    UUID restaurantId,
    String title,
    String description,
    LocalDate eventDate,
    String eventTime,
    String location,
    Integer maxPlaces,
    Integer remainingPlaces,
    String minTier,
    String image,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {
}
