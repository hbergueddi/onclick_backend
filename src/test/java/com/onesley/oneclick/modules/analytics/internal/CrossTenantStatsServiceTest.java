package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.CrossTenantDtos.CrossTenantRowDto;
import com.onesley.oneclick.modules.analytics.api.CrossTenantDtos.CrossTenantSummaryDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires isolés de {@link CrossTenantStatsService#summarize} (fonction pure C3).
 *
 * <p>L'agrégat SQL est couvert par {@code CrossTenantStatsIntegrationTest} ; ici on valide la
 * logique de consolidation (sommes + comptage actifs / inactifs &gt; 30j) sans DB.</p>
 */
class CrossTenantStatsServiceTest {

    private static BigDecimal bd(long v) { return BigDecimal.valueOf(v); }

    private static CrossTenantRowDto row(String status, long ca, long tickets, long resa,
                                         long clients, long restos, Integer daysSince) {
        return new CrossTenantRowDto(UUID.randomUUID(), "slug", "Name", status,
            restos, restos, bd(ca), BigDecimal.ZERO, 0, tickets, resa, clients, null, daysSince);
    }

    @Test
    void summarize_sumsAndCountsActiveInactive() {
        List<CrossTenantRowDto> rows = List.of(
            row("active", 1000, 10, 5, 8, 2, 3),    // actif + récent
            row("active", 500, 4, 2, 3, 1, 45),     // actif MAIS inactif > 30j
            row("paused", 0, 0, 0, 0, 1, null));    // en pause + jamais d'activité

        CrossTenantSummaryDto s = CrossTenantStatsService.summarize(rows);

        assertThat(s.totalTenants()).isEqualTo(3);
        assertThat(s.activeTenants()).isEqualTo(2);
        assertThat(s.totalRestaurants()).isEqualTo(4);
        assertThat(s.totalCa()).isEqualByComparingTo(bd(1500));
        assertThat(s.totalTickets()).isEqualTo(14);
        assertThat(s.totalReservations()).isEqualTo(7);
        assertThat(s.totalClients()).isEqualTo(11);
        // Seul le tenant actif inactif > 30j compte (le 'paused' est exclu du décompte d'inactivité).
        assertThat(s.inactiveTenantsCount()).isEqualTo(1);
    }

    @Test
    void summarize_empty_returnsZeros() {
        CrossTenantSummaryDto s = CrossTenantStatsService.summarize(List.of());
        assertThat(s.totalTenants()).isZero();
        assertThat(s.activeTenants()).isZero();
        assertThat(s.totalCa()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.inactiveTenantsCount()).isZero();
    }
}
