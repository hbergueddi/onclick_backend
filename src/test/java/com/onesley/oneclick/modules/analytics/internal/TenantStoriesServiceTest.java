package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantStoryDtos.TenantStoryDto;
import com.onesley.oneclick.modules.analytics.api.TenantStoryDtos.TenantStoriesSummaryDto;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires isolés du résumé des stories (C4.8b) — fonction pure
 * {@link TenantStoriesService#summarize}. L'agrégat SQL est couvert par l'intégration.
 */
class TenantStoriesServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-06T12:00:00Z");

    private static TenantStoryDto story(Instant publishAt, Instant expiresAt) {
        return new TenantStoryDto(UUID.randomUUID(), "u", "image", "c", 5, 0,
            publishAt, expiresAt, UUID.randomUUID(), "Auteur", NOW);
    }

    @Test
    void summarize_empty_isAllZero() {
        TenantStoriesSummaryDto s = TenantStoriesService.summarize(List.of(), NOW);
        assertThat(s.total()).isZero();
        assertThat(s.active()).isZero();
        assertThat(s.scheduled()).isZero();
        assertThat(s.expired()).isZero();
    }

    @Test
    void summarize_classifiesActiveScheduledExpired() {
        List<TenantStoryDto> rows = List.of(
            story(NOW.minus(Duration.ofHours(2)), NOW.plus(Duration.ofHours(22))), // active
            story(NOW.minus(Duration.ofDays(1)), null),                            // active (pas d'expiration)
            story(NOW.plus(Duration.ofHours(3)), NOW.plus(Duration.ofDays(1))),    // programmée
            story(NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofDays(1)))    // expirée
        );

        TenantStoriesSummaryDto s = TenantStoriesService.summarize(rows, NOW);

        assertThat(s.total()).isEqualTo(4);
        assertThat(s.active()).isEqualTo(2);
        assertThat(s.scheduled()).isEqualTo(1);
        assertThat(s.expired()).isEqualTo(1);
    }
}
