package com.onesley.oneclick.modules.analytics.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs du portail tenant-admin « Contrats » (C4.6), LECTURE SEULE.
 *
 * <p>Contrats des restaurants d'un tenant (contracts → restaurants → tenant) enrichis nom resto +
 * statut, avec un résumé (total, actifs, expirant sous 30j, ventilation par statut). L'édition/
 * signature reste côté super-admin OneClick (hors de cette vue).
 *
 * <p>Écarts assumés vs legacy Supabase : table {@code contracts} (pas {@code partner_contracts}) ;
 * {@code contractStart/End} = {@code starts_at/ends_at} (LocalDate) ; pas de {@code client_commission_rate}
 * en Spring (omis) ; statuts EN ({@code active}…).
 */
public final class TenantContractDtos {

    private TenantContractDtos() {}

    /** Contrat enrichi (resto). */
    public record TenantContractDto(
        UUID id,
        UUID restaurantId,
        String restaurantName,
        String restaurantCity,
        String status,
        LocalDate contractStart,
        LocalDate contractEnd,
        BigDecimal commissionRate,
        BigDecimal walletAdminRate,
        BigDecimal oneclickCommissionRate,
        boolean autoRenew,
        Instant signedAt,
        String contractNumber,
        Instant createdAt
    ) {}

    /** Résumé. */
    public record TenantContractsSummaryDto(
        long total,
        long active,
        long expiringSoon,          // statut actif + fin ≤ 30j
        Map<String, Long> byStatus
    ) {}

    /** Résultat complet de la vue « Contrats ». */
    public record TenantContractsResultDto(
        List<TenantContractDto> contracts,
        TenantContractsSummaryDto summary
    ) {}
}
