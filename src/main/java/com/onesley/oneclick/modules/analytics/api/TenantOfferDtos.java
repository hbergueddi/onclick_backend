package com.onesley.oneclick.modules.analytics.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs du portail tenant-admin « Mes promos » (C4.4).
 *
 * <p>Offres des restaurants d'un tenant, enrichies nom resto + impressions, avec un résumé
 * (actif / programmé / expiré). Agrégat serveur-side (native SQL : offers → restaurants → tenant ;
 * les offres Spring n'ont pas de {@code tenant_id} direct, le scope passe par le restaurant).
 *
 * <p>{@code isActive} = colonne {@code enabled} ; {@code claims} non disponible en V1 (0). Les
 * mutations (create/update/toggle/pin/delete) réutilisent {@code OfferController} — hors de ce module.
 */
public final class TenantOfferDtos {

    private TenantOfferDtos() {}

    /** Offre enrichie (resto + stats). */
    public record TenantOfferDto(
        UUID id,
        String title,
        String description,
        String type,
        Integer pts,
        String image,
        boolean isActive,
        boolean isPinned,
        Instant startsAt,
        Instant expiresAt,
        boolean pushNotify,
        List<String> segments,
        Instant createdAt,
        UUID restaurantId,
        String restaurantName,
        String restaurantCity,
        long impressions,
        long claims
    ) {}

    /** Résumé agrégé. */
    public record TenantOffersSummaryDto(
        long total,
        long active,
        long scheduled,   // starts_at dans le futur
        long expired      // expires_at dans le passé
    ) {}

    /** Référence restaurant (enrichissement + dropdown création). */
    public record OfferRestaurantRefDto(UUID id, String name, String city) {}

    /** Résultat complet de la vue « Mes promos ». */
    public record TenantOffersResultDto(
        List<TenantOfferDto> offers,
        TenantOffersSummaryDto summary,
        List<OfferRestaurantRefDto> restaurants
    ) {}
}
