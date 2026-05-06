package com.onesley.oneclick.dto.restaurant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code restaurant_gain_rules} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantGainRuleDto(
    UUID id,
    UUID restaurantId,
    String name,
    String description,
    String type,
    BigDecimal tauxConversion,
    BigDecimal minTicket,
    Integer maxPointsParTicket,
    Boolean enabled,
    Instant createdAt,
    Instant updatedAt,
    BigDecimal pointValueMad,
    UUID sourceRuleId,
    Integer welcomePointsDefault,
    Integer welcomePointsMax
) {
}
