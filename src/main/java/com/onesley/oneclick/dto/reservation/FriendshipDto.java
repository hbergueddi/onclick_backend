package com.onesley.oneclick.dto.reservation;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code friendships} (généré par scripts/scaffold-jpa.mjs).
 */
public record FriendshipDto(
    UUID id,
    UUID requesterId,
    UUID addresseeId,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
