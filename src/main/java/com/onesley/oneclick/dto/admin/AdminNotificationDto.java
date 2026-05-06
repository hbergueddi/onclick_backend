package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DTO pour {@code admin_notifications} (généré par scripts/scaffold-jpa.mjs).
 */
public record AdminNotificationDto(
    UUID id,
    Instant createdAt,
    UUID adminId,
    String type,
    String severity,
    String title,
    String description,
    String targetUrl,
    Map<String, Object> metadata,
    Instant readAt,
    Instant dismissedAt
) {
}
