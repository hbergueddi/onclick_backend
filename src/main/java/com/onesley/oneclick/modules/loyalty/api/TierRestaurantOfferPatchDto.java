package com.onesley.oneclick.modules.loyalty.api;

import jakarta.validation.constraints.Size;

/**
 * DTO payload pour PATCH /api/tier-offers/{id} — partial update (champs non-null
 * appliqués). Le restaurant et le tier ne sont pas modifiables après création.
 */
public record TierRestaurantOfferPatchDto(
    @Size(max = 100) String offerLabel,
    @Size(max = 40) String offerType,
    @Size(max = 50) String offerValue,
    @Size(max = 300) String description,
    Boolean enabled
) {
}
