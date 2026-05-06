package com.onesley.oneclick.dto.marketing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO pour {@code promo_notification_requests} (généré par scripts/scaffold-jpa.mjs).
 */
public record PromoNotificationRequestDto(
    UUID id,
    UUID offerId,
    UUID restaurantId,
    UUID requestedBy,
    String status,
    String message,
    List<String> targetSegments,
    String adminNote,
    UUID reviewedBy,
    Instant reviewedAt,
    Instant createdAt,
    Instant updatedAt,
    Instant pushSentAt,
    Integer pushSentCount,
    String pushError
) {
}
