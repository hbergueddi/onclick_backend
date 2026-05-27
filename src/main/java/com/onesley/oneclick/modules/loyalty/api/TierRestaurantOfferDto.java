package com.onesley.oneclick.modules.loyalty.api;

import java.time.Instant;
import java.util.UUID;

/** DTO public d'une offre fidélité par niveau & restaurant. */
public record TierRestaurantOfferDto(
    UUID id,
    UUID restaurantId,
    String tierName,
    String offerLabel,
    String offerType,
    String offerValue,
    String description,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
}
