package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantAnnouncementDtos.TenantAnnouncementDto;
import com.onesley.oneclick.modules.analytics.api.TenantAnnouncementDtos.TenantAnnouncementsSummaryDto;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires isolés du résumé des annonces (C4.8a) — fonction pure
 * {@link TenantAnnouncementsService#summarize}. L'agrégat SQL est couvert par l'intégration.
 */
class TenantAnnouncementsServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-06T12:00:00Z");

    private static TenantAnnouncementDto a(Instant publishAt, Instant archivedAt) {
        return new TenantAnnouncementDto(UUID.randomUUID(), "T", "B", null, "normal", false,
            publishAt, archivedAt, 1, UUID.randomUUID(), "Auteur", NOW);
    }

    @Test
    void summarize_empty_isAllZero() {
        TenantAnnouncementsSummaryDto s = TenantAnnouncementsService.summarize(List.of(), NOW);
        assertThat(s.total()).isZero();
        assertThat(s.active()).isZero();
        assertThat(s.scheduled()).isZero();
        assertThat(s.archived()).isZero();
    }

    @Test
    void summarize_classifiesActiveScheduledArchived() {
        List<TenantAnnouncementDto> rows = List.of(
            a(NOW.minus(Duration.ofDays(1)), null),                    // active (publiée, non archivée)
            a(NOW.plus(Duration.ofDays(2)), null),                     // programmée
            a(NOW.minus(Duration.ofDays(5)), NOW.minus(Duration.ofDays(1))) // archivée (priorité sur le reste)
        );

        TenantAnnouncementsSummaryDto s = TenantAnnouncementsService.summarize(rows, NOW);

        assertThat(s.total()).isEqualTo(3);
        assertThat(s.active()).isEqualTo(1);
        assertThat(s.scheduled()).isEqualTo(1);
        assertThat(s.archived()).isEqualTo(1);
    }
}
