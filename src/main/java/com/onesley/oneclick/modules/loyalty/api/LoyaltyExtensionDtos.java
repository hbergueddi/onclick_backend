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
        BigDecimal score,
        // D4 — enrichissement fiche réservation admin (label/étoiles + compteurs
        // réservations sur la fenêtre glissante de client_score_config).
        String label,
        BigDecimal stars,
        Long totalReservations,
        Long honorees,
        Long noShows
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

    /**
     * Distribution des membres par palier de fidélité (vue admin /fidelite).
     * memberCount = nb de clients dont les points globaux (somme des soldes de
     * leurs comptes) tombent dans le palier, bucketé sur {@code tiers.min_points}
     * du tenant du client. Paliers à 0 membre inclus (LEFT JOIN).
     */
    public record TierDistributionDto(
        UUID tierId,
        UUID tenantId,
        String name,
        Integer minPoints,
        Long memberCount
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

    /**
     * Agrégat plateforme de l'économie de points — page admin OneClick Lounge.
     * Calculé sur {@code loyalty_transactions} (earn/spend/expire), cross-restaurant.
     *
     * <ul>
     *   <li>{@code emitted}  — points crédités (points &gt; 0)</li>
     *   <li>{@code consumed} — points consommés (transactions {@code spend})</li>
     *   <li>{@code expired}  — points expirés (transactions {@code expire})</li>
     *   <li>{@code available} — {@code emitted - consumed - expired}</li>
     *   <li>{@code emittingCount} — nb de transactions émettrices (pour la moyenne/ticket)</li>
     *   <li>{@code byType} — répartition des émissions par catégorie (déduite du reason)</li>
     *   <li>{@code monthly} — tendance des 6 derniers mois</li>
     * </ul>
     */
    public record PointsEconomyDto(
        long emitted,
        long consumed,
        long expired,
        long available,
        long emittingCount,
        java.util.List<TypeBucket> byType,
        java.util.List<MonthlyPoint> monthly
    ) {
        public record TypeBucket(String type, long count) {}
        public record MonthlyPoint(String month, long emitted, long consumed, long expired) {}
    }
}
