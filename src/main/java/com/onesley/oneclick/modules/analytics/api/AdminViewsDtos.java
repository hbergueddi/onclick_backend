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
}
