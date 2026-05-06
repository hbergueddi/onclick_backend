package com.onesley.oneclick.dto.restaurant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO pour {@code restaurant_services} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantServiceDto(
    UUID id,
    UUID restaurantId,
    String name,
    String type,
    String heureDebut,
    String heureFin,
    List<String> joursActifs,
    Integer capaciteMax,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Integer clickgoQuota
) {
}
