package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantReservationDtos.TenantReservationDto;
import com.onesley.oneclick.modules.analytics.api.TenantReservationDtos.TenantReservationsSummaryDto;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires isolés du résumé CRM réservations (C4.3) — fonction pure
 * {@link TenantReservationsService#summarize}. L'agrégat SQL est couvert par l'intégration.
 */
class TenantReservationsServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-06T12:00:00Z");
    private static final UUID RESTO_A = UUID.randomUUID();
    private static final UUID RESTO_B = UUID.randomUUID();

    private static TenantReservationDto row(Instant at, String status, UUID restoId) {
        return new TenantReservationDto(UUID.randomUUID(), at, 2, status, null, NOW,
            restoId, "Resto", "Ville", UUID.randomUUID(), "A", "B", null, null);
    }

    @Test
    void summarize_empty_isAllZero() {
        TenantReservationsSummaryDto s = TenantReservationsService.summarize(List.of(), NOW);
        assertThat(s.total()).isZero();
        assertThat(s.upcomingCount()).isZero();
        assertThat(s.todayCount()).isZero();
        assertThat(s.pendingCount()).isZero();
        assertThat(s.byStatus()).isEmpty();
        assertThat(s.byRestaurant()).isEmpty();
    }

    @Test
    void summarize_countsStatusRestaurantAndWindows() {
        Instant todayStart = NOW.truncatedTo(ChronoUnit.DAYS);
        List<TenantReservationDto> rows = List.of(
            row(NOW, "pending", RESTO_A),                              // aujourd'hui + à venir + pending
            row(NOW.minus(Duration.ofDays(5)), "confirmed", RESTO_A),  // passé
            row(todayStart.plus(Duration.ofDays(3)), "pending", RESTO_B) // futur + à venir + pending
        );

        TenantReservationsSummaryDto s = TenantReservationsService.summarize(rows, NOW);

        assertThat(s.total()).isEqualTo(3);
        assertThat(s.byStatus()).containsEntry("pending", 2L).containsEntry("confirmed", 1L);
        assertThat(s.byRestaurant()).containsEntry(RESTO_A.toString(), 2L).containsEntry(RESTO_B.toString(), 1L);
        assertThat(s.upcomingCount()).isEqualTo(2);   // aujourd'hui + futur
        assertThat(s.todayCount()).isEqualTo(1);
        assertThat(s.pendingCount()).isEqualTo(2);
    }
}
