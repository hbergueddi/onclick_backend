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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link AdminStatsService} (L3 — modules.analytics).
 * 17 COUNT natifs agrégés ; branches tenant scoped vs platform-wide.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class AdminStatsServiceTest {

    @Mock EntityManager em;
    @Mock Query query;
    @InjectMocks AdminStatsService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "em", em);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(query.getSingleResult()).thenReturn(5L);
    }

    @Test
    void computeStats_platformWide() {
        var dto = service.computeStats(null);
        assertThat(dto.totalUsers()).isEqualTo(5L);
        assertThat(dto.tenantId()).isNull();
    }

    @Test
    void computeStats_tenantScoped() {
        UUID t = UUID.randomUUID();
        var dto = service.computeStats(t);
        assertThat(dto.totalUsers()).isEqualTo(5L);
        assertThat(dto.tenantId()).isEqualTo(t.toString());
    }
}
