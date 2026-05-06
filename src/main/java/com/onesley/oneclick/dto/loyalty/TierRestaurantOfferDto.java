package com.onesley.oneclick.dto.loyalty;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code tier_restaurant_offers} (généré par scripts/scaffold-jpa.mjs).
 */
public record TierRestaurantOfferDto(
    UUID id,
    UUID restaurantId,
    String tierName,
    String offerLabel,
    String offerType,
    String offerValue,
    String description,
    Boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
}
