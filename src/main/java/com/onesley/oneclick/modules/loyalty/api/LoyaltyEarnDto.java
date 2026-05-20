package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO payload pour earn points (Snap2Earn) — POST /api/loyalty/earn.
 */
public record LoyaltyEarnDto(
    @NotNull UUID clientId,
    @NotNull UUID restaurantId,
    @NotNull @Min(1) Integer points,
    BigDecimal amount,
    @Size(min = 1, max = 1024) String reason
) {
}
