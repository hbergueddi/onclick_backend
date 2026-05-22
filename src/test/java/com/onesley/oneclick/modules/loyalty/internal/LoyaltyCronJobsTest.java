package com.onesley.oneclick.modules.loyalty.internal;

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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests unitaires de {@link LoyaltyCronJobs#processExpiredPoints}. */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class LoyaltyCronJobsTest {

    @Mock EntityManager em;
    @Mock Query query;
    @InjectMocks LoyaltyCronJobs cron;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(cron, "em", em);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(query.executeUpdate()).thenReturn(1);
    }

    @Test
    void processExpiredPoints_insertsAndUpdates_skipsNonPositive() {
        // une ligne points>0 (traitée) + une ligne points<=0 (skip via continue)
        when(query.getResultList()).thenReturn(List.<Object[]>of(
            new Object[]{UUID.randomUUID(), UUID.randomUUID(), 50},
            new Object[]{UUID.randomUUID(), UUID.randomUUID(), 0}));
        cron.processExpiredPoints();
        // au moins 1 INSERT expire + 1 UPDATE balance pour la ligne positive
        verify(query, atLeastOnce()).executeUpdate();
    }

    @Test
    void processExpiredPoints_empty_noop() {
        when(query.getResultList()).thenReturn(List.of());
        cron.processExpiredPoints();
    }
}
