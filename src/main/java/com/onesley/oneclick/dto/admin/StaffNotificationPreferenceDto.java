package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code staff_notification_preferences} (généré par scripts/scaffold-jpa.mjs).
 */
public record StaffNotificationPreferenceDto(
    UUID id,
    UUID userId,
    Boolean booking,
    Boolean reservation,
    Boolean feedback,
    Boolean loyalty,
    Boolean system,
    Instant createdAt,
    Instant updatedAt
) {
}
