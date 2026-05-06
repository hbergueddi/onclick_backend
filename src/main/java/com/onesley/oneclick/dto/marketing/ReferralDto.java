package com.onesley.oneclick.dto.marketing;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code referrals} (généré par scripts/scaffold-jpa.mjs).
 */
public record ReferralDto(
    UUID id,
    UUID referrerId,
    String referredPhone,
    String referredName,
    UUID referredUserId,
    String status,
    Integer ptsAwarded,
    Instant createdAt,
    Instant activatedAt,
    UUID restaurantId
) {
}
