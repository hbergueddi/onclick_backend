package com.onesley.oneclick.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code system_health_checks} (généré par scripts/scaffold-jpa.mjs).
 */
public record SystemHealthCheckDto(
    UUID id,
    String metricName,
    BigDecimal metricValue,
    String metricUnit,
    String status,
    String details,
    Instant createdAt
) {
}
