package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantAnnouncementDtos.TenantAnnouncementDto;
import com.onesley.oneclick.modules.analytics.api.TenantAnnouncementDtos.TenantAnnouncementsResultDto;
import com.onesley.oneclick.modules.analytics.api.TenantAnnouncementDtos.TenantAnnouncementsSummaryDto;
import static com.onesley.oneclick.shared.Temporals.toInstant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service portail tenant-admin « Annonces » (C4.8a), LECTURE SEULE.
 *
 * <p>Native SQL (Modulith CLOSED ; calque {@code TenantClientsService}). Annonces du tenant
 * ({@code tenant_announcements}) JOIN users (auteur). Résumé = fonction pure {@link #summarize}.
 */
@Service
@Transactional(readOnly = true)
public class TenantAnnouncementsService {

    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    public TenantAnnouncementsResultDto list(UUID tenantId) {
        List<TenantAnnouncementDto> rows = new ArrayList<>();
        for (Object[] a : (List<Object[]>) em.createNativeQuery("""
            SELECT a.id, a.title, a.body, a.image_url, a.priority, a.is_pinned, a.publish_at,
                   a.archived_at, a.body_version, a.author_id, a.created_at,
                   TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, ''))
              FROM tenant_announcements a
              LEFT JOIN users u ON u.id = a.author_id
             WHERE a.tenant_id = :t AND a.deleted_at IS NULL
             ORDER BY a.is_pinned DESC, a.publish_at DESC
            """).setParameter("t", tenantId).getResultList()) {
            String author = (String) a[11];
            rows.add(new TenantAnnouncementDto(
                (UUID) a[0], (String) a[1], (String) a[2], (String) a[3], (String) a[4],
                bool(a[5]), toInstant(a[6]), toInstant(a[7]),
                a[8] == null ? 0 : ((Number) a[8]).intValue(),
                (UUID) a[9], (author == null || author.isBlank()) ? null : author, toInstant(a[10])));
        }
        return new TenantAnnouncementsResultDto(rows, summarize(rows, Instant.now()));
    }

    /**
     * Résumé des annonces — fonction pure, testable. {@code active} = non archivée + publiée
     * ({@code publishAt ≤ now}) ; {@code scheduled} = non archivée + {@code publishAt} futur ;
     * {@code archived} = {@code archivedAt} non null.
     */
    public static TenantAnnouncementsSummaryDto summarize(List<TenantAnnouncementDto> rows, Instant now) {
        long active = 0, scheduled = 0, archived = 0;
        for (TenantAnnouncementDto a : rows) {
            if (a.archivedAt() != null) {
                archived++;
            } else if (a.publishAt() != null && a.publishAt().isAfter(now)) {
                scheduled++;
            } else {
                active++;
            }
        }
        return new TenantAnnouncementsSummaryDto(rows.size(), active, scheduled, archived);
    }

    private static boolean bool(Object o) {
        return o instanceof Boolean b && b;
    }
}
