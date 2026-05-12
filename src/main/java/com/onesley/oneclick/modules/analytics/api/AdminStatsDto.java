package com.onesley.oneclick.modules.analytics.api;

/**
 * Agrégat KPI platform-wide pour le dashboard admin — Sprint G.2.4.
 *
 * <p>Calcul cross-modules via JPQL natives queries (pas d'import cross-module
 * d'entités, cohérence avec Modulith Type.CLOSED).
 */
public record AdminStatsDto(
    // Users
    long totalUsers,
    long totalClients,
    long totalRestaurateurs,
    long totalStaff,
    // Restaurants
    long totalRestaurants,
    long activeRestaurants,
    // Reservations
    long totalReservations,
    long pendingReservations,
    long confirmedReservations,
    long honoredReservations,
    // Loyalty
    long totalLoyaltyAccounts,
    long totalLoyaltyPoints,
    // Offers
    long totalOffers,
    long activeOffers,
    // Financial
    long totalContracts,
    long activeContracts,
    // Support
    long openSupportTickets,
    // Filter context
    String tenantId
) {
}
