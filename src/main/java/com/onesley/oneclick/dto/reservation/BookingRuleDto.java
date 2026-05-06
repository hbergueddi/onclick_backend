package com.onesley.oneclick.dto.reservation;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code booking_rules} (généré par scripts/scaffold-jpa.mjs).
 */
public record BookingRuleDto(
    UUID id,
    UUID restaurantId,
    String name,
    String description,
    String category,
    String value,
    Boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
}
