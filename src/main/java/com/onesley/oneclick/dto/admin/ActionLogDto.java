package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code action_logs} (généré par scripts/scaffold-jpa.mjs).
 */
public record ActionLogDto(
    UUID id,
    UUID restaurantId,
    UUID userId,
    String memberName,
    String action,
    String type,
    String details,
    String ip,
    Instant createdAt
) {
}
