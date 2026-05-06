package com.onesley.oneclick.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code system_alerts} (généré par scripts/scaffold-jpa.mjs).
 */
public record SystemAlertDto(
    UUID id,
    UUID ruleId,
    String metricName,
    BigDecimal metricValue,
    BigDecimal threshold,
    String severity,
    String message,
    Boolean acknowledged,
    UUID acknowledgedBy,
    Instant acknowledgedAt,
    Instant createdAt
) {
}
