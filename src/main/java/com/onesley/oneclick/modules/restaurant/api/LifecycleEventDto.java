package com.onesley.oneclick.modules.restaurant.api;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO public d'un événement de cycle de vie restaurant. {@code restaurantName}
 * est résolu côté service (jointure intra-module sur restaurants).
 */
public record LifecycleEventDto(
    UUID id,
    UUID restaurantId,
    String restaurantName,
    String event,
    String details,
    String actor,
    Instant createdAt
) {
}
