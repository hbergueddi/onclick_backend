package com.onesley.oneclick.modules.loyalty.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO public d'un palier de fidélité plateforme (OneClick Lounge).
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code LoyaltyTierRule.toDto()} (dépendance internal → api autorisée en
 * Modulith CLOSED).</p>
 */
public record LoyaltyTierRuleDto(
    UUID id,
    String name,
    String description,
    String type,
    BigDecimal conversionRate,
    BigDecimal minTicket,
    int maxPointsPerTicket,
    String periodType,
    int periodValue,
    int benefitDurationDays,
    BigDecimal minSpendMonthly,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
}
