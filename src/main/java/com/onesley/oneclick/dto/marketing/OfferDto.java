package com.onesley.oneclick.dto.marketing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO pour {@code offers} (généré par scripts/scaffold-jpa.mjs).
 */
public record OfferDto(
    UUID id,
    UUID restaurantId,
    String type,
    String title,
    String description,
    String image,
    Integer pts,
    Instant expiresAt,
    Boolean isActive,
    Instant createdAt,
    Instant updatedAt,
    List<String> segments,
    Boolean pushNotify,
    UUID campaignId,
    Instant startsAt,
    UUID tenantId,
    UUID createdBy,
    UUID modifiedBy
) {
}
