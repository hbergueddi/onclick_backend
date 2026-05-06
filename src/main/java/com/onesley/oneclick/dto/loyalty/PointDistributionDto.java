package com.onesley.oneclick.dto.loyalty;

import com.onesley.oneclick.entity.shared.DistributionSource;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code point_distributions} (généré par scripts/scaffold-jpa.mjs).
 */
public record PointDistributionDto(
    UUID id,
    UUID adminId,
    DistributionSource sourceType,
    UUID restaurantId,
    UUID clientId,
    String segment,
    Integer points,
    String reason,
    UUID campaignId,
    UUID offerId,
    String status,
    Instant createdAt
) {
}
