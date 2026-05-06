package com.onesley.oneclick.dto.restaurant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO read-only pour {@code v_restaurants_google} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantsGoogleViewDto(
    UUID id,
    String name,
    String googlePlaceId,
    BigDecimal googleRating,
    Integer googleReviewsCount,
    List<String> googlePhotos,
    String websiteUrl,
    BigDecimal latitude,
    BigDecimal longitude,
    Map<String, Object> openingHours,
    Instant googleUpdatedAt
) {
}
