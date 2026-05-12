package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/** DTO payload pour POST /api/loyalty/gain-rules. */
public record GainRuleCreateDto(
    @NotNull UUID restaurantId,
    @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal conversionRate,
    @Min(1) Integer capPerVisit,
    @Min(1) Integer capPerMonth,
    @DecimalMin("0.00") BigDecimal minAmount
) {
}
