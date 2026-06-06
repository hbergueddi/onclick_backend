package com.onesley.oneclick.modules.analytics.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs du portail tenant-admin « Équipe » (C4.5).
 *
 * <p>Staff agrégé de tous les restaurants d'un tenant (restaurant_staffs → restaurants → tenant),
 * enrichi user (nom/contact) + nom resto, avec un résumé (total, owners, managers, staff front,
 * staff cross-resto). Agrégat serveur-side (native SQL).
 *
 * <p>Écarts assumés vs legacy Supabase : {@code role} = colonne {@code role_code} ; pas de colonne
 * {@code status} ni {@code start_date} en Spring → {@code status} = « actif » (soft-delete via
 * {@code deleted_at}) et {@code startDate} = null. Les mutations (invite/update/remove) réutilisent
 * les endpoints staff existants — hors de ce module.
 */
public final class TenantStaffDtos {

    private TenantStaffDtos() {}

    /** Membre du staff (1 ligne = 1 affectation user × restaurant). */
    public record TenantStaffMemberDto(
        UUID staffId,
        UUID userId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String avatarUrl,
        String role,
        String status,
        Instant startDate,
        UUID restaurantId,
        String restaurantName
    ) {}

    /** Résumé de l'équipe. */
    public record TenantStaffSummaryDto(
        long total,
        long owners,
        long managers,
        long staff,
        long crossResto    // nb d'users présents sur 2+ restos
    ) {}

    /** Résultat complet de la vue « Équipe ». */
    public record TenantStaffResultDto(
        List<TenantStaffMemberDto> staff,
        TenantStaffSummaryDto summary
    ) {}
}
