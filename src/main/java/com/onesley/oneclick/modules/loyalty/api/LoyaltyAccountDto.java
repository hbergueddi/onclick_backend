package com.onesley.oneclick.modules.loyalty.api;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO public d'un compte fidélité.
 *
 * <p>Conversion Entity → DTO via {@code LoyaltyAccount.toDto()} (dépendance
 * internal → api autorisée en Modulith CLOSED) — laisse {@code restaurantName}/
 * {@code restaurantCuisine} à {@code null}.</p>
 *
 * <p>{@code restaurantName}/{@code restaurantCuisine} (anti-N+1) ne sont remplis
 * que par {@code findByClient} via une projection native JOIN restaurants
 * ({@code LoyaltyAccountWithRestaurantView}) — le module loyalty (CLOSED) ne peut
 * pas importer l'entité Restaurant, on reste au niveau SQL. Permet au Pocket
 * (Mes Points / historique) d'afficher le nom du resto sans 2ᵉ fetch.</p>
 */
public record LoyaltyAccountDto(
    UUID id,
    UUID clientId,
    UUID restaurantId,
    UUID tierId,
    Integer balance,
    Instant createdAt,
    String restaurantName,
    String restaurantCuisine
) {
}
