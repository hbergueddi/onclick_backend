package com.onesley.oneclick.modules.analytics.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link AdminStatsFullService} (L3 — modules.analytics).
 * compute(period) : couvre les 6 branches de periodRange/previousPeriodRange,
 * agrégats core/résa/tickets/trend/tops/staff + deltas vs période précédente.
 *
 * <p>1 seul compute() par test (les thenReturn ordonnés sont consommés en séquence).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class AdminStatsFullServiceTest {

    @Mock EntityManager em;
    @Mock Query query;
    @InjectMocks AdminStatsFullService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "em", em);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);

        Object[] core = { 1L, 2L, 2L, 10L, 5L, 3L, 4L, 2L, new BigDecimal("1000"), new BigDecimal("4.5") };
        Object[] ticketRow = { 5L, new BigDecimal("2500") };
        // getSingleResult : core, ticket, [delta-resa, delta-sum, delta-tickets]
        lenient().when(query.getSingleResult()).thenReturn(core, ticketRow, 10L, new BigDecimal("500"), 8L);

        List<Object[]> resa = List.<Object[]>of(new Object[]{"confirmed", 3L}, new Object[]{"pending", 2L});
        List<Object[]> trend = List.<Object[]>of(new Object[]{"2026-05-01", 1L, new BigDecimal("100"), 1L});
        List<Object[]> tops = List.<Object[]>of(new Object[]{"Resto", new BigDecimal("500"), 3L});
        List<Object[]> staff = List.<Object[]>of(new Object[]{"owner", 1L}, new Object[]{"unknown_role", 2L});
        // getResultList : resa, trend, tops, staff
        lenient().when(query.getResultList()).thenReturn(resa, trend, tops, staff);
    }

    @Test
    void compute_allTime_noDeltas() {
        var dto = service.compute(null);
        assertThat(dto.totalRestaurants()).isEqualTo(2L);
        assertThat(dto.totalReservations()).isEqualTo(5L);     // 3 + 2
        assertThat(dto.deltaReservations()).isNull();          // prevRange null → pas de delta
        assertThat(dto.staffBreakdown()).hasSize(2);
        assertThat(dto.topRestaurants()).hasSize(1);
    }

    @Test
    void compute_semaine_withDeltas() {
        var dto = service.compute("semaine");
        assertThat(dto.totalReservations()).isEqualTo(5L);
        assertThat(dto.deltaReservations()).isNotNull();       // prevRange non-null → delta calculé
    }

    @Test
    void compute_mois() {
        assertThat(service.compute("mois").totalRestaurants()).isEqualTo(2L);
    }

    @Test
    void compute_moisPasse() {
        assertThat(service.compute("mois_passe").totalRestaurants()).isEqualTo(2L);
    }

    @Test
    void compute_annee() {
        assertThat(service.compute("annee").totalRestaurants()).isEqualTo(2L);
    }

    @Test
    void compute_anneePasse() {
        assertThat(service.compute("annee_passe").totalRestaurants()).isEqualTo(2L);
    }

    @Test
    void compute_unknownPeriod_defaultsAllTime() {
        var dto = service.compute("n_importe_quoi");
        assertThat(dto.deltaReservations()).isNull();
    }
}
