package com.onesley.oneclick.shared.events;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand un client utilise des points pour une réduction (redemption).
 *
 * <p>Consommé par notification-service → notif "Vous avez utilisé X pts (-Y MAD)".
 */
public record LoyaltyRedeemedEvent(
    UUID redemptionId,
    UUID accountId,
    UUID clientId,
    UUID restaurantId,
    UUID tenantId,
    int pointsUsed,
    BigDecimal discountAmount,
    Instant occurredAt
) {
}
