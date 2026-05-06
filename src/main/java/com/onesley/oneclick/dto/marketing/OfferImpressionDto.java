package com.onesley.oneclick.dto.marketing;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code offer_impressions} (généré par scripts/scaffold-jpa.mjs).
 */
public record OfferImpressionDto(
    UUID id,
    UUID offerId,
    UUID userId,
    Instant viewedAt,
    String source
) {
}
