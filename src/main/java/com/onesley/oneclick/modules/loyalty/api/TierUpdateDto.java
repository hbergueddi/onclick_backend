package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * PATCH partiel d'un palier de fidélité ({@code tiers}) — admin /forge/regles.
 * Tous les champs sont optionnels : seuls les champs présents sont appliqués.
 * Aligné sur le modèle Spring réel (name / minPoints / bonusPercent / sortOrder) —
 * les concepts période/dépense/avantages relèvent de {@code loyalty_tier_rules}.
 */
public record TierUpdateDto(
    @Size(min = 1, max = 128) String name,
    @Min(0) Integer minPoints,
    @DecimalMin("0") BigDecimal bonusPercent,
    @Min(0) Integer sortOrder
) {}
