package com.onesley.oneclick.dto.reservation;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code friend_group_members} (généré par scripts/scaffold-jpa.mjs).
 */
public record FriendGroupMemberDto(
    UUID id,
    UUID groupId,
    UUID friendId,
    Instant createdAt
) {
}
