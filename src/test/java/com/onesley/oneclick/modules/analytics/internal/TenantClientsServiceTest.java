package com.onesley.oneclick.modules.analytics.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires isolés de la segmentation CRM (C4.1) — fonction pure
 * {@link TenantClientsService#segment}. L'agrégat SQL est couvert par le test d'intégration.
 */
class TenantClientsServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-06T12:00:00Z");
    private static final Instant RECENT = NOW.minus(Duration.ofDays(5));
    private static final Instant OLD = NOW.minus(Duration.ofDays(90));

    @Test
    void segment_neverVisited_isNouveau() {
        assertThat(TenantClientsService.segment(null, BigDecimal.ZERO, 0, null, NOW)).isEqualTo("nouveau");
    }

    @Test
    void segment_firstVisitWithin30d_isNouveau() {
        assertThat(TenantClientsService.segment(RECENT, new BigDecimal("5000"), 9, RECENT, NOW))
            .isEqualTo("nouveau");
    }

    @Test
    void segment_oldClient_highCa30j_isVip() {
        assertThat(TenantClientsService.segment(OLD, new BigDecimal("1000"), 1, RECENT, NOW)).isEqualTo("vip");
        assertThat(TenantClientsService.segment(OLD, new BigDecimal("2500"), 0, OLD, NOW)).isEqualTo("vip");
    }

    @Test
    void segment_oldClient_manyVisits30j_isVip() {
        assertThat(TenantClientsService.segment(OLD, BigDecimal.ZERO, 5, RECENT, NOW)).isEqualTo("vip");
    }

    @Test
    void segment_oldClient_recentLowActivity_isActif() {
        assertThat(TenantClientsService.segment(OLD, new BigDecimal("200"), 2, RECENT, NOW)).isEqualTo("actif");
    }

    @Test
    void segment_oldClient_noRecentActivity_isDormant() {
        assertThat(TenantClientsService.segment(OLD, BigDecimal.ZERO, 0, OLD, NOW)).isEqualTo("dormant");
        assertThat(TenantClientsService.segment(OLD, null, 0, null, NOW)).isEqualTo("dormant");
    }
}
