package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantStoryDtos.TenantStoriesResultDto;
import com.onesley.oneclick.modules.analytics.api.TenantStoryDtos.TenantStoriesSummaryDto;
import com.onesley.oneclick.modules.analytics.api.TenantStoryDtos.TenantStoryDto;
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
 * Service portail tenant-admin « Stories » (C4.8b), LECTURE SEULE.
 *
 * <p>Native SQL (Modulith CLOSED ; calque {@code TenantClientsService}). Stories du tenant
 * ({@code pcc_stories}) JOIN users (auteur). Résumé = fonction pure {@link #summarize}.
 */
@Service
@Transactional(readOnly = true)
public class TenantStoriesService {

    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    public TenantStoriesResultDto list(UUID tenantId) {
        List<TenantStoryDto> rows = new ArrayList<>();
        for (Object[] s : (List<Object[]>) em.createNativeQuery("""
            SELECT s.id, s.media_url, s.media_type, s.caption, s.duration_s, s.sort_order,
                   s.publish_at, s.expires_at, s.author_id, s.created_at,
                   TRIM(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, ''))
              FROM pcc_stories s
              LEFT JOIN users u ON u.id = s.author_id
             WHERE s.tenant_id = :t AND s.deleted_at IS NULL
             ORDER BY s.publish_at DESC, s.sort_order
            """).setParameter("t", tenantId).getResultList()) {
            String author = (String) s[10];
            rows.add(new TenantStoryDto(
                (UUID) s[0], (String) s[1], (String) s[2], (String) s[3],
                s[4] == null ? 0 : ((Number) s[4]).intValue(),
                s[5] == null ? 0 : ((Number) s[5]).intValue(),
                toInstant(s[6]), toInstant(s[7]),
                (UUID) s[8], (author == null || author.isBlank()) ? null : author, toInstant(s[9])));
        }
        return new TenantStoriesResultDto(rows, summarize(rows, Instant.now()));
    }

    /**
     * Résumé des stories — fonction pure, testable. {@code expired} = {@code expiresAt ≤ now} ;
     * sinon {@code scheduled} = {@code publishAt} futur ; sinon {@code active}.
     */
    public static TenantStoriesSummaryDto summarize(List<TenantStoryDto> rows, Instant now) {
        long active = 0, scheduled = 0, expired = 0;
        for (TenantStoryDto s : rows) {
            if (s.expiresAt() != null && !s.expiresAt().isAfter(now)) {
                expired++;
            } else if (s.publishAt() != null && s.publishAt().isAfter(now)) {
                scheduled++;
            } else {
                active++;
            }
        }
        return new TenantStoriesSummaryDto(rows.size(), active, scheduled, expired);
    }
}
