package com.onesley.oneclick.dto.loyalty;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code user_favorites} (généré par scripts/scaffold-jpa.mjs).
 */
public record UserFavoriteDto(
    UUID id,
    UUID userId,
    UUID restaurantId,
    Instant createdAt
) {
}
