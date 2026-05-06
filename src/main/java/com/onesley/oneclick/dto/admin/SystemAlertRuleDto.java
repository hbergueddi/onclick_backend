package com.onesley.oneclick.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO pour {@code system_alert_rules} (généré par scripts/scaffold-jpa.mjs).
 */
public record SystemAlertRuleDto(
    UUID id,
    String name,
    String metricName,
    String operator,
    BigDecimal threshold,
    String severity,
    Boolean enabled,
    Integer cooldownMinutes,
    Instant lastTriggeredAt,
    Instant createdAt,
    Instant updatedAt
) {
}
