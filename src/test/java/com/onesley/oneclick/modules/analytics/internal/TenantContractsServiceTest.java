package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantContractDtos.TenantContractDto;
import com.onesley.oneclick.modules.analytics.api.TenantContractDtos.TenantContractsSummaryDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires isolés du résumé des contrats (C4.6) — fonction pure
 * {@link TenantContractsService#summarize}. L'agrégat SQL est couvert par l'intégration.
 */
class TenantContractsServiceTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-06-06");

    private static TenantContractDto contract(String status, LocalDate end) {
        return new TenantContractDto(UUID.randomUUID(), UUID.randomUUID(), "R", "V", status,
            TODAY.minusDays(30), end, null, null, null, false, null, "C-1", null);
    }

    @Test
    void summarize_empty_isAllZero() {
        TenantContractsSummaryDto s = TenantContractsService.summarize(List.of(), TODAY);
        assertThat(s.total()).isZero();
        assertThat(s.active()).isZero();
        assertThat(s.expiringSoon()).isZero();
        assertThat(s.byStatus()).isEmpty();
    }

    @Test
    void summarize_countsActiveExpiringAndByStatus() {
        List<TenantContractDto> rows = List.of(
            contract("active", TODAY.plusDays(10)),  // actif + expire sous 30j
            contract("active", TODAY.plusDays(60)),  // actif, pas d'expiration proche
            contract("active", null),                // actif sans fin
            contract("draft", TODAY.plusDays(5))     // pas actif (même si fin proche)
        );

        TenantContractsSummaryDto s = TenantContractsService.summarize(rows, TODAY);

        assertThat(s.total()).isEqualTo(4);
        assertThat(s.active()).isEqualTo(3);
        assertThat(s.expiringSoon()).isEqualTo(1);
        assertThat(s.byStatus()).containsEntry("active", 3L).containsEntry("draft", 1L);
    }

    @Test
    void summarize_expiredOrToday_notCountedAsExpiringSoon() {
        List<TenantContractDto> rows = List.of(
            contract("active", TODAY),               // aujourd'hui → pas « à venir »
            contract("active", TODAY.minusDays(1))   // déjà expiré
        );
        TenantContractsSummaryDto s = TenantContractsService.summarize(rows, TODAY);
        assertThat(s.expiringSoon()).isZero();
        assertThat(s.active()).isEqualTo(2);
    }
}
