package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantOfferDtos.TenantOfferDto;
import com.onesley.oneclick.modules.analytics.api.TenantOfferDtos.TenantOffersSummaryDto;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires isolés du résumé des promos (C4.4) — fonction pure
 * {@link TenantOffersService#summarize}. L'agrégat SQL est couvert par l'intégration.
 */
class TenantOffersServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-06T12:00:00Z");

    private static TenantOfferDto offer(Instant startsAt, Instant expiresAt, boolean active) {
        return new TenantOfferDto(UUID.randomUUID(), "T", null, "promo", 10, null, active, false,
            startsAt, expiresAt, false, List.of(), NOW, UUID.randomUUID(), "R", "V", 0L, 0L);
    }

    @Test
    void summarize_empty_isAllZero() {
        TenantOffersSummaryDto s = TenantOffersService.summarize(List.of(), NOW);
        assertThat(s.total()).isZero();
        assertThat(s.active()).isZero();
        assertThat(s.scheduled()).isZero();
        assertThat(s.expired()).isZero();
    }

    @Test
    void summarize_classifiesExpiredScheduledActive() {
        Instant past = NOW.minus(Duration.ofDays(10));
        Instant future = NOW.plus(Duration.ofDays(10));
        List<TenantOfferDto> offers = List.of(
            offer(past, past, true),       // expirée (expiresAt < now)
            offer(future, future, true),   // programmée (startsAt > now)
            offer(past, future, true),     // active (en cours + enabled)
            offer(past, future, false)     // ni active ni programmée ni expirée (désactivée)
        );

        TenantOffersSummaryDto s = TenantOffersService.summarize(offers, NOW);

        assertThat(s.total()).isEqualTo(4);
        assertThat(s.expired()).isEqualTo(1);
        assertThat(s.scheduled()).isEqualTo(1);
        assertThat(s.active()).isEqualTo(1);
    }
}
