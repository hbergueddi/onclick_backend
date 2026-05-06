package com.onesley.oneclick.dto.restaurant;

import com.onesley.oneclick.entity.restaurant.OpeningHourSlot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO complet pour {@code restaurants} — utilisé sur les détails (Spotlight page).
 *
 * <p>Pour les listes (Explore page, ~100 cards), on créera un {@code RestaurantSummaryDto}
 * en Phase 11 (sous-ensemble : id, name, city, cuisine, image, rating, openNow, tags).
 */
public record RestaurantDto(
    UUID id,
    String name,
    String city,
    String cuisine,
    String budget,
    BigDecimal rating,
    Integer reviewsCount,
    String image,
    String phone,
    String address,
    String description,
    Boolean openNow,
    List<String> tags,
    Integer loungePts,
    String status,
    Integer maxStaff,
    UUID groupId,
    String googlePlaceId,
    List<OpeningHourSlot> openingHours,
    String websiteUrl,
    BigDecimal latitude,
    BigDecimal longitude,
    BigDecimal googleRating,
    Integer googleReviewsCount,
    List<String> googlePhotos,
    Instant googleUpdatedAt,
    Instant onboardingCompletedAt,
    String referralCode,
    UUID referredById,
    Instant referredActivatedAt,
    UUID tenantId,
    Instant createdAt,
    Instant updatedAt
) {
}
