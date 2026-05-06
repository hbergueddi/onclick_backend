package com.onesley.oneclick.dto.loyalty;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code point_gifts} (généré par scripts/scaffold-jpa.mjs).
 */
public record PointGiftDto(
    UUID id,
    UUID senderId,
    UUID receiverId,
    UUID restaurantId,
    Integer points,
    String message,
    Instant createdAt
) {
}
