package com.onesley.oneclick.dto.loyalty;

import com.onesley.oneclick.entity.shared.PunchCardActivity;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code loyalty_punch_cards} (généré par scripts/scaffold-jpa.mjs).
 */
public record LoyaltyPunchCardDto(
    UUID id,
    UUID tenantId,
    UUID clientId,
    PunchCardActivity activityType,
    Integer countPunched,
    Integer threshold,
    Integer redeemedCount,
    Instant lastPunchedAt,
    Instant lastRedeemedAt,
    Instant createdAt,
    Instant updatedAt
) {
}
