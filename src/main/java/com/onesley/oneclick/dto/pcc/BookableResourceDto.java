package com.onesley.oneclick.dto.pcc;

import com.onesley.oneclick.entity.shared.BookablePaymentMode;
import com.onesley.oneclick.entity.shared.BookableResourceType;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DTO pour {@code bookable_resources} (généré par scripts/scaffold-jpa.mjs).
 */
public record BookableResourceDto(
    UUID id,
    UUID tenantId,
    UUID restaurantId,
    BookableResourceType resourceType,
    String name,
    Integer capacity,
    Integer slotDurationMinutes,
    Integer maxInvitees,
    Map<String, Object> openingHours,
    BookablePaymentMode paymentMode,
    Map<String, Object> pricing,
    Boolean enabled,
    Instant createdAt,
    Instant updatedAt,
    String imageUrl
) {
}
