package com.onesley.oneclick.modules.promotion.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO public d'une offre / promotion.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Offer.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 */
public record OfferDto(
    UUID id, UUID restaurantId, String title, String description,
    Instant startsAt, Instant expiresAt, BigDecimal discountPct, BigDecimal discountAmount,
    boolean enabled, Instant createdAt
) {
}
