package com.onesley.oneclick.shared.events;

import org.springframework.modulith.events.Externalized;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event publié par loyalty-service quand un client gagne des points (scan ticket).
 *
 * <p>Consommé par :
 * <ul>
 *   <li>notification-service → notif "Vous avez gagné X pts chez {restaurant}"</li>
 *   <li>analytics → enregistrement métrique (Phase 3+)</li>
 * </ul>
 */
@Externalized("loyalty.earned")
public record LoyaltyEarnedEvent(
    UUID transactionId,
    UUID accountId,
    UUID clientId,
    UUID restaurantId,
    UUID tenantId,
    int points,
    BigDecimal amount,
    String reason,
    Instant occurredAt
) {
}
