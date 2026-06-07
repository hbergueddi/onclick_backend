package com.onesley.oneclick.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand un staff demande un OTP de conversion (Gap #2,
 * {@code RedemptionOtpService.requestOtp}).
 *
 * <p>Consommé par {@code NotificationEventHandler} (core/notification) qui crée la
 * notification in-app porteuse du CODE pour le client — côté SERVEUR (le staff n'a
 * pas {@code CREATE:NOTIFICATIONS}, et seul le client doit voir le code). Le code en
 * clair ne transite que dans cet event in-process puis dans la notification du client.
 */
public record RedemptionOtpRequestedEvent(
    UUID clientId,
    String code,
    String restaurantName,
    int points,
    BigDecimal discountDh,
    Instant occurredAt
) {
}
