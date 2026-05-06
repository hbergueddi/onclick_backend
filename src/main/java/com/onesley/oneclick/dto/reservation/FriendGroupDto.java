package com.onesley.oneclick.dto.reservation;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code friend_groups} (généré par scripts/scaffold-jpa.mjs).
 */
public record FriendGroupDto(
    UUID id,
    UUID ownerId,
    String name,
    String emoji,
    Instant createdAt,
    Instant updatedAt
) {
}
