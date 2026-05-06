package com.onesley.oneclick.dto.loyalty;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code expired_points} (généré par scripts/scaffold-jpa.mjs).
 */
public record ExpiredPointDto(
    UUID id,
    UUID clientId,
    UUID restaurantId,
    UUID originalPointId,
    Integer pointsExpired,
    Instant earnedAt,
    Instant expiredAt,
    Instant createdAt
) {
}
