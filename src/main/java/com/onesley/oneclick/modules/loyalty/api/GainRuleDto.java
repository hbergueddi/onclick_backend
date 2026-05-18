package com.onesley.oneclick.modules.loyalty.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO public d'une règle de gain par restaurant (override des loyalty_rules globaux).
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait
 * via {@code GainRule.toDto()} (dépendance internal → api autorisée
 * en Modulith CLOSED).</p>
 */
public record GainRuleDto(
    UUID id,
    UUID restaurantId,
    BigDecimal conversionRate,
    Integer capPerVisit,
    Integer capPerMonth,
    BigDecimal minAmount,
    boolean isActive,
    /** Bonus de bienvenue par défaut crédité à l'inscription (cf V28 + EnrollmentService). */
    int welcomePointsDefault,
    /** Plafond du bonus de bienvenue (anti-abus staff). */
    int welcomePointsMax,
    Instant createdAt
) {
}
