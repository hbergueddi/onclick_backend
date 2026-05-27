package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO payload pour PATCH /api/loyalty/tier-rules/{id} — tous les champs optionnels.
 *
 * <p>Convention PATCH : seuls les champs non-null sont appliqués (partial update).
 * Pour désactiver un palier sans le supprimer, passer {@code enabled=false}.
 */
public record LoyaltyTierRulePatchDto(
    @Size(max = 50) String name,
    @Size(max = 500) String description,
    @Size(max = 30) String type,
    @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal conversionRate,
    @DecimalMin("0.00") BigDecimal minTicket,
    @Min(0) Integer maxPointsPerTicket,
    @Pattern(regexp = "week|month") String periodType,
    @Min(1) Integer periodValue,
    @Min(0) Integer benefitDurationDays,
    @DecimalMin("0.00") BigDecimal minSpendMonthly,
    Boolean enabled
) {
}
