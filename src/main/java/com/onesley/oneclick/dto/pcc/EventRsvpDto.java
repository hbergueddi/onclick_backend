package com.onesley.oneclick.dto.pcc;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code event_rsvps} (généré par scripts/scaffold-jpa.mjs).
 */
public record EventRsvpDto(
    UUID id,
    UUID eventId,
    UUID userId,
    String status,
    Instant createdAt,
    Instant updatedAt
) {
}
