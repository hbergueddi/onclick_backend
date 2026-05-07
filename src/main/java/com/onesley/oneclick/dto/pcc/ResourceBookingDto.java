package com.onesley.oneclick.dto.pcc;

import com.onesley.oneclick.entity.shared.ResourceBookingStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DTO pour {@code resource_bookings} (généré par scripts/scaffold-jpa.mjs).
 */
public record ResourceBookingDto(
    UUID id,
    UUID resourceId,
    UUID organizerId,
    Instant startAt,
    Instant endAt,
    Integer partySize,
    Map<String, Object> invitees,
    ResourceBookingStatus status,
    Map<String, Object> pricingSnapshot,
    String notes,
    Instant createdAt,
    Instant updatedAt,
    Instant reminderJ1SentAt,
    Instant reminderH2SentAt,
    UUID createdBy,
    UUID modifiedBy
) {
}
