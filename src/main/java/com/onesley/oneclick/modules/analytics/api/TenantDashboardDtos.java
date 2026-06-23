package com.onesley.oneclick.modules.analytics.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * DTOs du cockpit tenant-admin (C4.0) — vue d'ensemble « business » d'un tenant, 1:1 avec le
 * cockpit tenant du legacy 4Click.
 *
 * <p>Agrégat serveur-side (native SQL tenant-scopé, Modulith CLOSED : aucun import d'entité
 * cross-module, calque {@code AdminStatsFullService} + réutilise les services {@code Tenant*}). CA =
 * {@code loyalty_transactions.amount} (earn Snap2Earn) ; clients actifs = clients distincts ayant ≥ 1
 * ticket Snap2Earn OU ≥ 1 réservation comptée dans la fenêtre ; points distribués =
 * {@code loyalty_transactions.points} (earn). Deltas = variation % vs la période 30j précédente,
 * {@code null} quand la base précédente est 0 (le front affiche « nouveau »).
 *
 * <p>Le palier ({@code tier}) des top clients est <b>dérivé backend</b> depuis la table canonique
 * {@code tiers} du tenant (source unique, comme {@code LoyaltyTierResolver}). Si le tenant n'a aucun
 * palier configuré, {@code tier == null} et le front dérive le libellé depuis {@code points} via son
 * hook tier-thresholds (jamais de seuils hardcodés ici).
 *
 * <p>Temps réel : le cockpit s'abonne à {@code /topic/admin/tenant-kpis/{tenantId}} (publisher
 * {@code TenantKpisPublisher}, SUPERADMIN-only via {@code StompAuthChannelInterceptor}) et re-fetch
 * ce endpoint à chaque message WS — pas de polling client.
 */
public final class TenantDashboardDtos {

    private TenantDashboardDtos() {}

    /** Cockpit complet d'un tenant (KPIs + deltas + tendance + tops + à venir + alertes). */
    public record TenantDashboardDto(
        BigDecimal ca30j,
        long reservations30j,
        long clientsActifs30j,
        long pointsDistribues30j,
        Integer deltaCa,             // % vs 30j précédents ; null si base précédente = 0 (« nouveau »)
        Integer deltaReservations,
        Integer deltaClients,
        Integer deltaPoints,
        long restaurantsActifs,
        long restaurantsTotal,
        long offresActives,
        List<DailyPointDto> dailyTrend,
        List<TopRestaurantDto> topRestaurants,
        List<TopClientDto> topClients,
        List<UpcomingReservationDto> upcomingReservations,
        List<AlertDto> alerts
    ) {}

    /** Point de tendance quotidienne (CA + réservations d'un jour, 30 derniers jours). */
    public record DailyPointDto(
        String date,                 // YYYY-MM-DD
        String label,                // J/M
        BigDecimal ca,
        long reservations
    ) {}

    /** Top restaurant du tenant (par CA 30j). */
    public record TopRestaurantDto(
        UUID id,
        String name,
        String city,
        BigDecimal ca,
        long tickets
    ) {}

    /** Top client du tenant (par CA 30j). {@code tier} null → dérivé front depuis {@code points}. */
    public record TopClientDto(
        UUID id,
        String firstName,
        String lastName,
        BigDecimal ca,
        long tickets,
        long points,
        String tier
    ) {}

    /** Réservation à venir (7 prochains jours, statuts pending/confirmed/counter_proposed). */
    public record UpcomingReservationDto(
        UUID id,
        String restaurantName,
        String clientName,
        String date,                 // YYYY-MM-DD (UTC)
        String time,                 // HH:mm (UTC)
        int guestsCount,
        String status
    ) {}

    /** Alerte opérationnelle dérivée (type info|warning|danger). */
    public record AlertDto(
        String type,
        String icon,
        String title,
        String description
    ) {}
}
