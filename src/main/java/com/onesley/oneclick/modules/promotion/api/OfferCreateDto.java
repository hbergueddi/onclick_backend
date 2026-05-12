package com.onesley.oneclick.modules.promotion.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OfferCreateDto(
    @NotNull UUID restaurantId,
    @NotBlank String title,
    String description,
    @NotNull Instant startsAt,
    @NotNull Instant expiresAt,
    @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPct,
    @DecimalMin("0.00") BigDecimal discountAmount
) {
}
