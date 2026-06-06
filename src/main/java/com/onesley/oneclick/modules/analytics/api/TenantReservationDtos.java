package com.onesley.oneclick.modules.analytics.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs du portail tenant-admin « Mes réservations » (C4.3).
 *
 * <p>Vue transverse des réservations des restaurants d'un tenant sur une fenêtre glissante,
 * enrichies nom resto + nom/contact client, avec un résumé (par statut, par resto, à venir,
 * aujourd'hui, en attente). Agrégat serveur-side ({@code reservations.tenant_id} direct).
 *
 * <p>Écarts assumés vs legacy Supabase : pas de colonne {@code service} (omise) ; date+heure
 * exposées via {@code reservationAt} (Instant unique) ; statuts EN ({@code pending} = « demandée »).
 * La mutation de statut réutilise le PATCH réservation existant (pas dans ce module).
 */
public final class TenantReservationDtos {

    private TenantReservationDtos() {}

    /** Réservation enrichie (resto + client). */
    public record TenantReservationDto(
        UUID id,
        Instant reservationAt,
        int couverts,
        String status,
        String notes,
        Instant createdAt,
        UUID restaurantId,
        String restaurantName,
        String restaurantCity,
        UUID clientId,
        String clientFirstName,
        String clientLastName,
        String clientPhone,
        String clientEmail
    ) {}

    /** Résumé agrégé de la liste. */
    public record TenantReservationsSummaryDto(
        long total,
        Map<String, Long> byStatus,
        Map<String, Long> byRestaurant,
        long upcomingCount,    // reservationAt >= aujourd'hui
        long todayCount,
        long pendingCount      // status = 'pending'
    ) {}

    /** Référence restaurant (pour les filtres). */
    public record RestaurantRefDto(UUID id, String name) {}

    /** Résultat complet de la vue « Mes réservations ». */
    public record TenantReservationsResultDto(
        List<TenantReservationDto> reservations,
        TenantReservationsSummaryDto summary,
        List<RestaurantRefDto> restaurants
    ) {}
}
