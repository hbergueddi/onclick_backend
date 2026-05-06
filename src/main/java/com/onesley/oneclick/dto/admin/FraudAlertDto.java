package com.onesley.oneclick.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code fraud_alerts} (généré par scripts/scaffold-jpa.mjs).
 */
public record FraudAlertDto(
    UUID id,
    String type,
    String severity,
    UUID restaurantId,
    UUID clientId,
    String description,
    String status,
    BigDecimal montant,
    Instant createdAt,
    Instant updatedAt
) {
}
