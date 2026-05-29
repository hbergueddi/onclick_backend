package com.onesley.oneclick.modules.analytics.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTOs Sprint H — vues admin enrichies (users, recycling-pool, HI cockpit).
 */
public final class AdminViewsDtos {

    private AdminViewsDtos() {}

    public record AdminUserDto(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String roleCode,
        UUID tenantId,
        Integer loyaltyPointsBalance,
        Long reservationsCount,
        Long scannedTicketsCount,
        Instant createdAt
    ) {}

    public record AdminWalletSummaryDto(
        BigDecimal totalCredit,
        BigDecimal totalDebit,
        BigDecimal balance,
        Long transactionsCount,
        BigDecimal monthCredit,
        BigDecimal monthDebit
    ) {}

    public record AdminWalletTransactionDto(
        UUID id,
        UUID userId,
        UUID restaurantId,
        BigDecimal amount,
        String direction,
        String reason,
        Instant createdAt
    ) {}

    public record RecyclingPoolDto(
        BigDecimal totalPool,
        BigDecimal monthlyInflow,
        BigDecimal monthlyOutflow,
        Long activeContributors,
        BigDecimal averageTicket
    ) {}

    public record AdminHICockpitDto(
        Long restaurantsCount,
        Long contractsActive,
        Long contractsExpired,
        BigDecimal monthlyRevenue,
        BigDecimal yearlyRevenue,
        Long ticketsLast30d
    ) {}

    /**
     * Rollup d'UN restaurant pour le dashboard groupe (B1) — remplace le fan-out
     * N+1 ({@code Promise.all(ids.map(...))} × 6 sources). Le service agrège les 6
     * sources en 5 requêtes natives groupées par {@code restaurant_id}.
     *
     * <p>{@code honored} est compté côté serveur sur le statut canonique EN
     * ('honored') — le frontend l'utilise tel quel (pas de traduction EN/FR).
     */
    public record GroupRestaurantRollupDto(
        UUID restaurantId,
        BigDecimal totalCA,        // SUM(loyalty_transactions.amount) earn snap2earn
        Long totalPoints,          // SUM(loyalty_accounts.balance)
        BigDecimal walletBalance,  // SUM(wallet_transactions.amount)
        Long reservations,         // COUNT(reservations)
        Long honored,              // COUNT(reservations WHERE status='honored')
        Long tickets,              // COUNT(loyalty_transactions earn snap2earn)
        Long staff                 // COUNT(restaurant_staffs)
    ) {}
}
