package com.onesley.oneclick.modules.analytics.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTOs du portail tenant-admin « Stories » (C4.8b), LECTURE SEULE (oversight super-admin).
 *
 * <p>Stories d'un tenant ({@code pcc_stories}) enrichies auteur, avec un résumé (total, actives =
 * publiées non expirées, programmées = publish_at futur, expirées). La création reste dans l'app
 * staff du tenant.
 */
public final class TenantStoryDtos {

    private TenantStoryDtos() {}

    /** Story enrichie (auteur). */
    public record TenantStoryDto(
        UUID id,
        String mediaUrl,
        String mediaType,
        String caption,
        int durationS,
        int sortOrder,
        Instant publishAt,
        Instant expiresAt,
        UUID authorId,
        String authorName,
        Instant createdAt
    ) {}

    /** Résumé. */
    public record TenantStoriesSummaryDto(
        long total,
        long active,        // publiée + non expirée
        long scheduled,     // publish_at futur
        long expired        // expires_at ≤ now
    ) {}

    /** Résultat complet de la vue « Stories ». */
    public record TenantStoriesResultDto(
        List<TenantStoryDto> stories,
        TenantStoriesSummaryDto summary
    ) {}
}
