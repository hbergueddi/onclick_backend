package com.onesley.oneclick.modules.analytics.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs du CRM tenant-admin (C4.1) — vue « Mes clients » d'un tenant.
 *
 * <p>Un « client du tenant » = utilisateur ayant au moins 1 ticket (Snap2Earn) OU 1 réservation
 * dans un restaurant du tenant. Agrégats calculés serveur-side (native SQL groupé, pattern
 * {@code CrossTenantStatsService}) — pas de fan-out navigateur. CA = {@code loyalty_transactions.amount}
 * (earn Snap2Earn) ; points = {@code loyalty_accounts.balance}.
 */
public final class TenantClientDtos {

    private TenantClientDtos() {}

    /** Ligne CRM (liste « Mes clients »). */
    public record TenantClientDto(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String city,
        String avatarUrl,
        BigDecimal caTotal,
        BigDecimal ca30j,
        long visitsTotal,
        long visits30j,
        long reservations30j,
        long pointsSolde,
        Instant firstVisitAt,
        Instant lastVisitAt,
        UUID favoriteRestaurantId,
        String favoriteRestaurantName,
        String segment
    ) {}

    /** Événement de la timeline d'un client (ticket / réservation / points). */
    public record ClientTimelineEventDto(
        String id,
        String type,            // ticket | reservation | points
        Instant date,
        String restaurantName,
        String label,
        BigDecimal amount,      // CA (ticket)
        Integer points,         // points (loyalty)
        String status,          // statut (réservation)
        Integer couverts        // couverts (réservation)
    ) {}

    /** KPIs 360° d'un client. */
    public record TenantClientKpiDto(
        BigDecimal caTotal,
        BigDecimal ca30j,
        long visitsTotal,
        long visits30j,
        long pointsSolde,
        String segment,
        Instant firstVisitAt,
        Instant lastVisitAt,
        BigDecimal avgTicket
    ) {}

    /** Top restaurant fréquenté par un client. */
    public record TenantTopRestaurantDto(
        UUID id,
        String name,
        long visits,
        BigDecimal ca
    ) {}

    /** Profil client (fiche). */
    public record TenantClientProfileDto(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        String city,
        String avatarUrl,
        Instant createdAt
    ) {}

    /** Fiche 360° d'un client du tenant. */
    public record TenantClientDetailDto(
        TenantClientProfileDto profile,
        TenantClientKpiDto kpi,
        List<TenantTopRestaurantDto> topRestaurants,
        List<ClientTimelineEventDto> timeline
    ) {}
}
