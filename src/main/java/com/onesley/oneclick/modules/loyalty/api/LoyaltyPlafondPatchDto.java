package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * DTO payload pour PATCH /api/loyalty/plafonds/{id} — tous champs optionnels
 * (partial update : seuls les non-null sont appliqués).
 */
public record LoyaltyPlafondPatchDto(
    @Size(max = 100) String name,
    @Pattern(regexp = "client|restaurant|global") String scope,
    @DecimalMin("0.00") BigDecimal value,
    @Size(max = 30) String unit,
    @Size(max = 500) String description,
    Boolean enabled
) {
}
