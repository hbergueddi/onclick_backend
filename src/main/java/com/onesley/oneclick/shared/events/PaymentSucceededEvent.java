package com.onesley.oneclick.shared.events;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event publié par payment-service quand un paiement est confirmé.
 *
 * <p>Consommé par :
 * <ul>
 *   <li>notification-service → notif "Paiement confirmé {amount} {currency}"</li>
 *   <li>loyalty-service → crédit points si le paiement = restaurant scannable (futur)</li>
 * </ul>
 */
public record PaymentSucceededEvent(
    UUID paymentId,
    UUID userId,
    UUID tenantId,
    BigDecimal amount,
    String currency,
    String provider,
    String referenceType,
    UUID referenceId,
    Instant occurredAt
) {
}
