package com.onesley.oneclick.modules.restaurant_referral.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * DTOs publics du module {@code restaurant_referral} (parrainage owner → owner).
 *
 * <p>Records immuables + Bean Validation — seul contrat exposé hors module (les entités JPA restent
 * dans {@code internal}). Calque {@code SocialDtos} (style record + validation).
 */
public final class RestaurantReferralDtos {

    private RestaurantReferralDtos() {}

    /**
     * Vue publique d'un parrainage resto→resto.
     *
     * @param status {@code pending} (code généré, pas encore activé) ou {@code activated}
     * @param rewardPoints points crédités au PARRAIN à l'activation (0 tant que pending)
     */
    public record RestaurantReferralDto(
        UUID id,
        UUID referrerRestaurantId,
        UUID referrerUserId,
        String referralCode,
        UUID refereeRestaurantId,
        UUID refereeUserId,
        String status,
        Instant activatedAt,
        int rewardPoints,
        Instant rewardedAt,
        UUID tenantId,
        Instant createdAt
    ) {}

    /**
     * Payload d'activation d'un code de parrainage par l'owner du resto FILLEUL.
     *
     * @param code               code du resto PARRAIN (non vide)
     * @param refereeRestaurantId resto FILLEUL (celui de l'appelant) qui consomme le code
     */
    public record ActivateRestaurantReferralDto(
        @NotBlank @Size(min = 1, max = 64) String code,
        @NotNull UUID refereeRestaurantId
    ) {}
}
