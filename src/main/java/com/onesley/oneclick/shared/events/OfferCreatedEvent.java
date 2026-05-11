package com.onesley.oneclick.shared.events;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand un restaurant crée une nouvelle promotion.
 *
 * <p>Consommé par notification-service → broadcast push aux clients abonnés
 * (campaign automatique segmentée par favoris / proximité).
 */
public record OfferCreatedEvent(
    UUID offerId,
    UUID restaurantId,
    UUID tenantId,
    String title,
    String description,
    Instant startsAt,
    Instant expiresAt,
    BigDecimal discountPct,
    BigDecimal discountAmount
) {
}
