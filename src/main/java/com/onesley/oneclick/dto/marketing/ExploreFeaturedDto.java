package com.onesley.oneclick.dto.marketing;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code explore_featured} (généré par scripts/scaffold-jpa.mjs).
 */
public record ExploreFeaturedDto(
    UUID id,
    UUID restaurantId,
    Integer position,
    Instant startsAt,
    Instant expiresAt,
    Boolean isActive,
    String label,
    String notes,
    UUID createdBy,
    Instant createdAt,
    Instant updatedAt
) {
}
