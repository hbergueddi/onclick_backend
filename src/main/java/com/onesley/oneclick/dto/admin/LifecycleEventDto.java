package com.onesley.oneclick.dto.admin;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code lifecycle_events} (généré par scripts/scaffold-jpa.mjs).
 */
public record LifecycleEventDto(
    UUID id,
    UUID restaurantId,
    String event,
    String actor,
    String details,
    Instant createdAt
) {
}
