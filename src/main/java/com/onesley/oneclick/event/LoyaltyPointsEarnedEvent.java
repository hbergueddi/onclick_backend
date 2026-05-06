package com.onesley.oneclick.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Émis quand des points fidélité sont crédités (Snap2Earn, parrainage, bonus).
 *
 * <p>{@code source} = "snap2earn" / "referral" / "honored_reservation" / "manual" /…
 *
 * <p>Listeners attendus :
 * <ul>
 *   <li>Notification client ("Vous avez gagné X points !")</li>
 *   <li>Mise à jour des stats tier (Saphir/Rubis/Émeraude)</li>
 *   <li>Audit log + Sentry breadcrumb</li>
 *   <li>Rebuild du wallet recycling pool si snap2earn</li>
 * </ul>
 */
public record LoyaltyPointsEarnedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID loyaltyPointId,
    UUID clientId,
    UUID restaurantId,
    Integer points,
    BigDecimal amountTtc,
    String source
) implements DomainEvent {

    public static LoyaltyPointsEarnedEvent of(
        UUID loyaltyPointId, UUID clientId, UUID restaurantId,
        Integer points, BigDecimal amountTtc, String source
    ) {
        return new LoyaltyPointsEarnedEvent(
            UUID.randomUUID(), Instant.now(),
            loyaltyPointId, clientId, restaurantId, points, amountTtc, source
        );
    }

    @Override
    public String aggregateType() { return "loyalty_points"; }

    @Override
    public UUID aggregateId() { return loyaltyPointId; }
}
