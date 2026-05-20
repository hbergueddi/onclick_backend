package com.onesley.oneclick.modules.promotion.api;

import jakarta.validation.constraints.*;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OfferCreateDto(
    @NotNull UUID restaurantId,
    @NotBlank @Size(min = 1, max = 128) String title,
    @Size(min = 1, max = 1024) String description,
    @NotNull Instant startsAt,
    @NotNull Instant expiresAt,
    @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal discountPct,
    @DecimalMin("0.00") BigDecimal discountAmount,
    @Pattern(regexp = "^(promo|bonus|reco)$") @Size(min = 1, max = 64) String type,
    @Positive Integer pts,
    Boolean pushNotify,
    @Size(min = 1, max = 512) String image,
    List<String> segments
) {
}
