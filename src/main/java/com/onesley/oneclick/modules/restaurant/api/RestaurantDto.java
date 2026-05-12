package com.onesley.oneclick.modules.restaurant.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO public d'un restaurant.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Restaurant.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 *
 * <p>V16 — Trois attributs éditoriaux ({@code budget}, {@code tags},
 * {@code loungePts}) plus l'URL {@code image}. Tous nullables (sauf
 * {@code loungePts} défaut 0 et {@code tags} liste vide) pour rétro-compat
 * avec les rows pré-V16.</p>
 */
public record RestaurantDto(
    UUID id,
    UUID tenantId,
    String name,
    String description,
    String phone,
    String address,
    String city,
    BigDecimal latitude,
    BigDecimal longitude,
    String status,
    String budget,
    List<String> tags,
    Integer loungePts,
    String image,
    Instant createdAt
) {
}
