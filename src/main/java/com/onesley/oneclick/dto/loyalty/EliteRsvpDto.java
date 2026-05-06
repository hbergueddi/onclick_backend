package com.onesley.oneclick.dto.loyalty;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code elite_rsvps} (généré par scripts/scaffold-jpa.mjs).
 */
public record EliteRsvpDto(
    UUID id,
    UUID eventId,
    UUID userId,
    String status,
    Instant createdAt
) {
}
