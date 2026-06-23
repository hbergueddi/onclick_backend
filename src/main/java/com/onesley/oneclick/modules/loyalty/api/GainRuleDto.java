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
    Instant createdAt,
    /**
     * Valeur monétaire d'un point (1 pt = {@code pointValueMad} MAD), source de
     * vérité {@code loyalty_rules.point_value} (≠ table {@code gain_rules}).
     * Permet aux clients de calculer la rédemption Snap2Earn :
     * plafond points = {@code floor(montant / pointValueMad)} ;
     * remise MAD = {@code min(points × pointValueMad, montant)}.
     * Défaut {@code 1.0000} (rétro-compat : aujourd'hui tout vaut 1 pt = 1 MAD).
     */
    BigDecimal pointValueMad,
    // ─── Lot 4b — champs RuleBuilder legacy (V107, nullable) ────────────────────
    /** Type de période d'évaluation : {@code week} | {@code month} (nullable). */
    String evalPeriodType,
    /** Nombre de périodes d'évaluation (nullable). */
    Integer evalPeriodValue,
    /** Durée du bénéfice en jours (nullable). */
    Integer benefitDurationDays,
    /** Seuil de dépense mensuel d'activation (MAD, nullable). */
    BigDecimal minSpendMonthly
) {
}
