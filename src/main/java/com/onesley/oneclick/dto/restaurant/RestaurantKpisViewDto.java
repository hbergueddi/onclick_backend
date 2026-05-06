package com.onesley.oneclick.dto.restaurant;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO read-only pour {@code v_restaurant_kpis} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantKpisViewDto(
    UUID restaurantId,
    String name,
    String city,
    UUID groupId,
    BigDecimal totalCa,
    Long ticketCount,
    Long totalPointsIssued,
    Long totalReservations,
    Long reservationsHonorees,
    Long reservationsNoShow,
    Long activeReservations,
    Long staffCount
) {
}
