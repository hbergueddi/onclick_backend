package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.TenantRestaurantDtos.DailyTrendPointDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires isolés des fonctions pures de {@link TenantRestaurantsService} (C4.2) :
 * variation de CA + fusion de tendance quotidienne + label de date. L'agrégat SQL est couvert
 * par le test d'intégration.
 */
class TenantRestaurantsServiceTest {

    @Test
    void deltaCa_noBase_isNull() {
        assertThat(TenantRestaurantsService.deltaCa(new BigDecimal("500"), null)).isNull();
        assertThat(TenantRestaurantsService.deltaCa(new BigDecimal("500"), BigDecimal.ZERO)).isNull();
    }

    @Test
    void deltaCa_computesSignedPercent() {
        assertThat(TenantRestaurantsService.deltaCa(new BigDecimal("1500"), new BigDecimal("1000"))).isEqualTo(50);
        assertThat(TenantRestaurantsService.deltaCa(new BigDecimal("500"), new BigDecimal("1000"))).isEqualTo(-50);
        assertThat(TenantRestaurantsService.deltaCa(new BigDecimal("1000"), new BigDecimal("1000"))).isEqualTo(0);
    }

    @Test
    void dayLabel_formatsDayMonth() {
        assertThat(TenantRestaurantsService.dayLabel("2026-06-06")).isEqualTo("6/6");
        assertThat(TenantRestaurantsService.dayLabel("2026-12-25")).isEqualTo("25/12");
    }

    @Test
    void mergeTrend_unionSortedWithLabels() {
        Map<String, BigDecimal> ca = new LinkedHashMap<>();
        ca.put("2026-06-02", new BigDecimal("100"));
        Map<String, Long> resa = new LinkedHashMap<>();
        resa.put("2026-06-01", 3L);

        List<DailyTrendPointDto> trend = TenantRestaurantsService.mergeTrend(ca, resa);

        assertThat(trend).hasSize(2);
        assertThat(trend.get(0).date()).isEqualTo("2026-06-01");
        assertThat(trend.get(0).label()).isEqualTo("1/6");
        assertThat(trend.get(0).ca()).isEqualByComparingTo("0");
        assertThat(trend.get(0).reservations()).isEqualTo(3L);
        assertThat(trend.get(1).date()).isEqualTo("2026-06-02");
        assertThat(trend.get(1).ca()).isEqualByComparingTo("100");
        assertThat(trend.get(1).reservations()).isEqualTo(0L);
    }
}
