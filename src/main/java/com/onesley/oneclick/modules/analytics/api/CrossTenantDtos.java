package com.onesley.oneclick.modules.analytics.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs « CrossTenantDashboard » (C3) — vue d'oiseau super-admin des tenants whitelabel.
 *
 * <p>Snapshot poussé en temps réel via STOMP ({@code /topic/admin/cross-tenant}) + exposé en REST.
 * Records immutables à {@code equals} par valeur (détection de changement du publisher). KPIs
 * calculés sur une fenêtre glissante de N jours (CA/tickets/clients/réservations) + delta vs la
 * période précédente de même durée.</p>
 */
public final class CrossTenantDtos {

    private CrossTenantDtos() {}

    /**
     * Ligne par tenant. {@code caPeriod}/{@code caPrevious} = somme des montants de tickets
     * Snap2Earn (loyalty_transactions earn) des restos du tenant sur la fenêtre ; {@code deltaCaPct}
     * = variation %. {@code clientsActifs} = clients distincts ayant scanné un ticket sur la période.
     */
    public record CrossTenantRowDto(
        UUID tenantId,
        String tenantSlug,
        String tenantName,
        String tenantStatus,
        long restaurantsTotal,
        long restaurantsActifs,
        BigDecimal caPeriod,
        BigDecimal caPrevious,
        int deltaCaPct,
        long ticketsPeriod,
        long reservationsPeriod,
        long clientsActifs,
        Instant lastActivityAt,
        Integer daysSinceLastActivity
    ) {}

    /** Agrégat consolidé (somme des lignes) — KPIs d'en-tête. */
    public record CrossTenantSummaryDto(
        long totalTenants,
        long activeTenants,
        long totalRestaurants,
        BigDecimal totalCa,
        long totalTickets,
        long totalReservations,
        long totalClients,
        long inactiveTenantsCount
    ) {}

    /** Snapshot complet (lignes triées par CA décroissant + résumé). */
    public record CrossTenantStatsDto(
        List<CrossTenantRowDto> rows,
        CrossTenantSummaryDto summary
    ) {}
}
