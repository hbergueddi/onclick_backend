package com.onesley.oneclick.modules.analytics.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs du portail tenant-admin « Annonces » (C4.8a), LECTURE SEULE (oversight super-admin).
 *
 * <p>Annonces d'un tenant ({@code tenant_announcements}, Lot 8) enrichies auteur, avec un résumé
 * (total, actives = publiées non archivées, programmées = publish_at futur, archivées). La rédaction
 * reste dans l'app staff du tenant.
 */
public final class TenantAnnouncementDtos {

    private TenantAnnouncementDtos() {}

    /** Annonce enrichie (auteur). */
    public record TenantAnnouncementDto(
        UUID id,
        String title,
        String body,
        String imageUrl,
        String priority,
        boolean pinned,
        Instant publishAt,
        Instant archivedAt,
        int bodyVersion,
        UUID authorId,
        String authorName,
        Instant createdAt
    ) {}

    /** Résumé. */
    public record TenantAnnouncementsSummaryDto(
        long total,
        long active,        // publiée (publish_at ≤ now) + non archivée
        long scheduled,     // publish_at futur
        long archived
    ) {}

    /** Résultat complet de la vue « Annonces ». */
    public record TenantAnnouncementsResultDto(
        List<TenantAnnouncementDto> announcements,
        TenantAnnouncementsSummaryDto summary
    ) {}
}
