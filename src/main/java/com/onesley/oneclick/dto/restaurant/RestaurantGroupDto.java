package com.onesley.oneclick.dto.restaurant;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code restaurant_groups} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantGroupDto(
    UUID id,
    String name,
    String description,
    String logoUrl,
    Instant createdAt,
    Instant updatedAt,
    UUID ownerUserId,
    UUID tenantId
) {
}
