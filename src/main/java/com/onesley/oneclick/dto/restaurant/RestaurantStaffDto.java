package com.onesley.oneclick.dto.restaurant;

import com.onesley.oneclick.entity.shared.StaffRole;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO pour {@code restaurant_staff} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantStaffDto(
    UUID id,
    UUID userId,
    UUID restaurantId,
    StaffRole staffRole,
    Instant createdAt,
    String status,
    LocalDate startDate
) {
}
