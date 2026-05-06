package com.onesley.oneclick.dto.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code client_ratings} (généré par scripts/scaffold-jpa.mjs).
 */
public record ClientRatingDto(
    UUID id,
    UUID clientId,
    BigDecimal rating,
    Integer totalHonored,
    Integer totalNoShow,
    Boolean isNew,
    Instant updatedAt,
    BigDecimal visibleRating,
    Integer visibleTotalHonored,
    Integer visibleTotalNoShow,
    Boolean visibleIsNew,
    BigDecimal pendingRating,
    Integer pendingTotalHonored,
    Integer pendingTotalNoShow,
    Boolean pendingIsNew,
    Instant pendingVisibleAt
) {
}
