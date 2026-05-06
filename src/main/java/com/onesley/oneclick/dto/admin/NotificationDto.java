package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code notifications} (généré par scripts/scaffold-jpa.mjs).
 */
public record NotificationDto(
    UUID id,
    UUID userId,
    String title,
    String message,
    String type,
    Boolean read,
    String link,
    Instant createdAt,
    UUID restaurantId
) {
}
