package com.onesley.oneclick.modules.analytics.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * AdminStats enrichi Sprint H — shape compatible legacy useAdminStats.
 *
 * <p>Inclut tous les KPIs platform-wide + deltas + trends + charts + breakdowns.
 * Une seule query optimisée native SQL : ~14 COUNT + 1 trend aggregation.
 */
public record AdminStatsFullDto(
    long totalGroups,
    long totalRestaurants,
    long activeRestaurants,
    long totalProfiles,
    long totalClients,
    long totalStaff,
    long totalReservations,
    Map<String, Long> reservationsByStatus,
    long totalLoyaltyPoints,
    long totalOffers,
    long activeOffers,
    long totalScannedTickets,
    BigDecimal totalScannedAmount,
    BigDecimal avgRating,

    // Deltas (% vs previous period)
    Long deltaReservations,
    Long deltaScannedAmount,
    Long deltaTickets,

    // Charts
    List<CuisineDistribDto> cuisineDistribution,
    List<DailyTrendDto> dailyTrend,
    List<TopRestaurantDto> topRestaurants,
    List<StaffBreakdownDto> staffBreakdown
) {
    public record CuisineDistribDto(String name, long value) {}
    public record DailyTrendDto(String date, long reservations, BigDecimal ca, long tickets) {}
    public record TopRestaurantDto(String name, BigDecimal ca, long tickets) {}
    public record StaffBreakdownDto(String role, String label, long count) {}
}
