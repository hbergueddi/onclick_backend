package com.onesley.oneclick.modules.restaurant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** DTO payload pour POST /api/lifecycle-events (journalisation d'un événement). */
public record LifecycleEventCreateDto(
    @NotBlank @Size(max = 50) String event,
    UUID restaurantId,
    @Size(max = 1000) String details,
    @Size(max = 200) String actor
) {
}
