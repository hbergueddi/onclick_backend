package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** DTO payload pour POST /api/tier-offers. */
public record TierRestaurantOfferCreateDto(
    @NotNull UUID restaurantId,
    @NotBlank @Size(max = 40) String tierName,
    @NotBlank @Size(max = 100) String offerLabel,
    @Size(max = 40) String offerType,
    @Size(max = 50) String offerValue,
    @Size(max = 300) String description,
    Boolean enabled
) {
}
