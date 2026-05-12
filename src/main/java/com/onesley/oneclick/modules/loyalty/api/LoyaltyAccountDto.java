package com.onesley.oneclick.modules.loyalty.api;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO public d'un compte fidélité.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait
 * via {@code LoyaltyAccount.toDto()} (dépendance internal → api autorisée
 * en Modulith CLOSED).</p>
 */
public record LoyaltyAccountDto(
    UUID id,
    UUID clientId,
    UUID restaurantId,
    UUID tierId,
    Integer balance,
    Instant createdAt
) {
}
