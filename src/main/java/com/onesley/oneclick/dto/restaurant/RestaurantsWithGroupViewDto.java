package com.onesley.oneclick.dto.restaurant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO read-only pour {@code v_restaurants_with_group} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantsWithGroupViewDto(
    UUID id,
    String name,
    String city,
    String cuisine,
    String budget,
    BigDecimal rating,
    String image,
    String status,
    UUID groupId,
    Integer loungePts,
    Instant createdAt,
    BigDecimal googleRating,
    String groupName,
    UUID groupOwnerId
) {
}
