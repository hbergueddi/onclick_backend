package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * DTO payload pour PATCH /api/loyalty/gain-rules/{id} — tous les champs optionnels.
 *
 * <p>Convention PATCH : seuls les champs non-null sont appliqués (partial update).
 * Pour désactiver une règle sans la supprimer, passer {@code isActive=false}.
 */
public record GainRulePatchDto(
    @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal conversionRate,
    @Min(1) Integer capPerVisit,
    @Min(1) Integer capPerMonth,
    @DecimalMin("0.00") BigDecimal minAmount,
    Boolean isActive,
    /** Bonus bienvenue par défaut. CHECK DB : welcomePointsMax >= welcomePointsDefault. */
    @Min(0) Integer welcomePointsDefault,
    /** Plafond bonus bienvenue (anti-abus). */
    @Min(0) Integer welcomePointsMax,
    // ─── Lot 4b — champs RuleBuilder legacy (optionnels) ────────────────────────
    @DecimalMin("0.0000") BigDecimal pointValueMad,
    @Pattern(regexp = "^(week|month)$", message = "evalPeriodType doit être 'week' ou 'month'") String evalPeriodType,
    @Min(1) Integer evalPeriodValue,
    @Min(1) Integer benefitDurationDays,
    @DecimalMin("0.00") BigDecimal minSpendMonthly
) {
}
