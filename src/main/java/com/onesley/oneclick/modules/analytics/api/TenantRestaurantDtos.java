package com.onesley.oneclick.modules.analytics.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs du portail tenant-admin « Mes restaurants » (C4.2).
 *
 * <p>Liste enrichie des restaurants d'un tenant (KPIs 30j par resto) + fiche détaillée
 * (KPIs + delta + tendance quotidienne + staff + offres actives + dernières réservations).
 * Agrégats calculés serveur-side (native SQL, {@code restaurants.tenant_id} direct). CA =
 * {@code loyalty_transactions.amount} (earn Snap2Earn) ; staff « actif » = {@code deleted_at IS NULL}.
 *
 * <p>Écarts assumés vs legacy Supabase (schéma Spring) : pas de colonne {@code rating} plain
 * (seul {@code googleRating}/{@code googleReviewsCount}) ; réservations exposées via
 * {@code reservationAt} (Instant unique) au lieu de {@code date}+{@code heure} séparés.
 */
public final class TenantRestaurantDtos {

    private TenantRestaurantDtos() {}

    /** Ligne « Mes restaurants » (liste enrichie KPI 30j). */
    public record TenantRestaurantDto(
        UUID id,
        String name,
        String city,
        String cuisine,
        String image,
        String status,
        BigDecimal googleRating,
        Integer googleReviewsCount,
        String phone,
        String address,
        BigDecimal ca30j,
        long tickets30j,
        long reservations30j,
        long staffCount,
        Instant lastActivityAt
    ) {}

    /** Identité complète d'un restaurant (fiche). */
    public record RestaurantInfoDto(
        UUID id,
        String name,
        String city,
        String cuisine,
        String image,
        String status,
        BigDecimal googleRating,
        Integer googleReviewsCount,
        String phone,
        String address,
        String websiteUrl,
        String openingHours,
        BigDecimal latitude,
        BigDecimal longitude,
        Instant createdAt
    ) {}

    /** KPIs 30j d'un restaurant + delta vs période précédente. */
    public record RestaurantKpiDto(
        BigDecimal ca30j,
        BigDecimal ca30jPrev,
        Integer deltaCa,           // % vs 30j précédents ; null si pas de base
        long tickets30j,
        long reservations30j,
        long staffCount,
        long pointsDistribues30j
    ) {}

    /** Point de tendance quotidienne (CA + réservations d'un jour). */
    public record DailyTrendPointDto(
        String date,               // YYYY-MM-DD
        String label,              // J/M
        BigDecimal ca,
        long reservations
    ) {}

    /** Membre du staff d'un restaurant. */
    public record StaffMemberDto(
        UUID id,
        UUID userId,
        String role,
        String firstName,
        String lastName,
        String status
    ) {}

    /** Offre active d'un restaurant. */
    public record OfferInfoDto(
        UUID id,
        String title,
        String description,
        String type,
        Integer pts,
        Instant expiresAt,
        String image
    ) {}

    /** Réservation récente (fiche resto). */
    public record RecentReservationDto(
        UUID id,
        String clientName,
        Instant reservationAt,
        Integer couverts,
        String status
    ) {}

    /** Fiche complète d'un restaurant du tenant. */
    public record TenantRestaurantDetailDto(
        RestaurantInfoDto restaurant,
        RestaurantKpiDto kpi,
        List<DailyTrendPointDto> dailyTrend,
        List<StaffMemberDto> staff,
        List<OfferInfoDto> activeOffers,
        List<RecentReservationDto> recentReservations
    ) {}
}
