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
    BigDecimal discountAmount,
    /**
     * Destinataires « clients ayant mis ce resto en favori » (CH-1) — résolus côté
     * {@code OfferService} (requête native sur {@code user_favorites}) et portés sur l'event,
     * UNIQUEMENT si l'offre a {@code push_notify=true} (sinon liste vide). Frontière Modulith :
     * {@code core.notification} n'a qu'à itérer.
     */
    java.util.List<UUID> favoriteRecipientIds
) {
}
