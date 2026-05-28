package com.onesley.oneclick.modules.loyalty.api;

import com.onesley.oneclick.modules.loyalty.internal.AIUsage;
import com.onesley.oneclick.modules.loyalty.internal.ClientRating;
import com.onesley.oneclick.modules.loyalty.internal.RestaurantRestitution;
import com.onesley.oneclick.modules.loyalty.internal.RestaurantTierStatus;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTOs Sprint H — extensions admin/whitelabel pour le module loyalty.
 */
public final class LoyaltyExtensionDtos {

    private LoyaltyExtensionDtos() {}

    public record ClientRatingDto(
        UUID id,
        UUID userId,
        UUID reservationId,
        BigDecimal rating,
        BigDecimal visibleRating,
        BigDecimal pendingRating,
        String reason,
        BigDecimal delta,
        Instant createdAt
    ) {
        public static ClientRatingDto from(ClientRating r) {
            return new ClientRatingDto(
                r.getId(), r.getUserId(), r.getReservationId(),
                r.getRating(), r.getVisibleRating(), r.getPendingRating(),
                r.getReason(), r.getDelta(), r.getCreatedAt()
            );
        }
    }

    public record ClientScoreDto(
        UUID userId,
        BigDecimal averageRating,
        Long ratingsCount,
        BigDecimal score
    ) {}

    /**
     * Configuration singleton du moteur de notation client (V52).
     * Alimente /reservations · onglet Scoring (seuils + règles de calcul).
     */
    public record ClientScoreConfigDto(
        UUID id,
        Integer minReservations,
        BigDecimal seuilExcellent,
        BigDecimal seuilFiable,
        BigDecimal seuilMoyen,
        BigDecimal scoreInitial,
        BigDecimal penaliteNoShow,
        Integer honoreesPourRemonter,
        BigDecimal gainParPalier,
        Integer fenetreMois,
        Instant updatedAt
    ) {}

    /** PATCH partiel de la configuration de notation (champs présents seulement). */
    public record ClientScoreConfigPatchDto(
        @Min(0) Integer minReservations,
        @DecimalMin("0") BigDecimal seuilExcellent,
        @DecimalMin("0") BigDecimal seuilFiable,
        @DecimalMin("0") BigDecimal seuilMoyen,
        @DecimalMin("0") BigDecimal scoreInitial,
        @DecimalMin("0") BigDecimal penaliteNoShow,
        @Min(0) Integer honoreesPourRemonter,
        @DecimalMin("0") BigDecimal gainParPalier,
        @Min(1) Integer fenetreMois
    ) {}

    public record AIUsageDto(
        UUID userId,
        Integer promptCount,
        Instant lastPromptAt,
        Integer dailyLimit,
        Integer remaining,
        Instant resetsAt
    ) {
        public static AIUsageDto from(AIUsage u, int dailyLimit) {
            int used = u != null && u.getPromptCount() != null ? u.getPromptCount() : 0;
            Instant resetsAt = null;
            if (u != null && u.getLastPromptAt() != null) {
                resetsAt = u.getLastPromptAt().plusSeconds(86_400);
            }
            return new AIUsageDto(
                u != null ? u.getUserId() : null,
                used,
                u != null ? u.getLastPromptAt() : null,
                dailyLimit,
                Math.max(0, dailyLimit - used),
                resetsAt
            );
        }
    }

    public record RestaurantRestitutionDto(
        UUID id,
        UUID restaurantId,
        BigDecimal amount,
        Integer points,
        String reason,
        String status,
        Instant createdAt
    ) {
        public static RestaurantRestitutionDto from(RestaurantRestitution r) {
            return new RestaurantRestitutionDto(
                r.getId(), r.getRestaurantId(), r.getAmount(), r.getPoints(),
                r.getReason(), r.getStatus(), r.getCreatedAt()
            );
        }
    }

    public record RestaurantTierStatusDto(
        UUID restaurantId,
        String currentTier,
        Integer pointsEarned,
        Instant lastEvaluatedAt
    ) {
        public static RestaurantTierStatusDto from(RestaurantTierStatus s) {
            return new RestaurantTierStatusDto(
                s.getRestaurantId(), s.getCurrentTier(),
                s.getPointsEarned(), s.getLastEvaluatedAt()
            );
        }
    }

    public record ExpiredPointsAdminDto(
        UUID userId,
        String userName,
        Integer pointsExpired,
        Instant expiredAt,
        UUID restaurantId,
        String restaurantName
    ) {}

    public record PointDistributionDto(
        UUID id,
        UUID userId,
        UUID restaurantId,
        Integer points,
        String reason,
        Instant createdAt
    ) {}
}
