package com.onesley.oneclick.dto.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO read-only pour {@code client_visible_ratings} (généré par scripts/scaffold-jpa.mjs).
 */
public record ClientVisibleRatingDto(
    UUID id,
    UUID clientId,
    BigDecimal rating,
    Integer totalHonored,
    Integer totalNoShow,
    Boolean isNew,
    Instant updatedAt,
    Boolean hasPending,
    Instant pendingVisibleAt
) {
}
