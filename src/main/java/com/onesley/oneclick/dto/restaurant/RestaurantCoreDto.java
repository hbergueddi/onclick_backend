package com.onesley.oneclick.dto.restaurant;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO projection pour la vue {@code v_restaurants_core} — utilisé sur la page
 * Explore (liste de cards).
 *
 * <p>Sous-ensemble des colonnes du DTO complet, optimisé pour le payload réseau
 * (12 champs au lieu de 33 pour {@link RestaurantDto}).
 */
public record RestaurantCoreDto(
    UUID id,
    String name,
    String city,
    String cuisine,
    String budget,
    BigDecimal rating,
    Integer reviewsCount,
    String image,
    String status,
    UUID groupId,
    Integer loungePts,
    Instant createdAt
) {
}
