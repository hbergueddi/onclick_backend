package com.onesley.oneclick.modules.financial.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.shared.events.ContractExpiringSoonEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests unitaires de {@link FinancialCronJobs} — renew contracts + alerte expiration + génération factures. */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class FinancialCronJobsTest {

    @Mock EntityManager em;
    @Mock Query query;
    @Mock ApplicationEventPublisher events;
    @Mock UserDirectoryApi userDirectory;
    @InjectMocks FinancialCronJobs cron;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(cron, "em", em);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(query.executeUpdate()).thenReturn(3);
    }

    @Test
    void renewContracts_runs() {
        cron.renewContracts(); // native UPDATE → executeUpdate stub, pas d'exception
    }

    @Test
    void alertExpiringContracts_noAdmins_skips() {
        when(userDirectory.adminUserIds()).thenReturn(List.of());
        cron.alertExpiringContracts();
        verify(events, never()).publishEvent(any());
    }

    @Test
    void alertExpiringContracts_publishesEventPerContract_perMilestone() {
        when(userDirectory.adminUserIds()).thenReturn(List.of(UUID.randomUUID()));
        // 1 contrat dû par requête × 3 jalons → 3 events.
        when(query.getResultList()).thenReturn(List.<Object[]>of(
            new Object[]{UUID.randomUUID(), UUID.randomUUID(), "OCHI-001", "Resto X", "12/06/2026"}));
        cron.alertExpiringContracts();
        verify(events, times(3)).publishEvent(any(ContractExpiringSoonEvent.class));
    }

    @Test
    void generateInvoicesForPeriod_invalid_throws() {
        assertThatThrownBy(() -> cron.generateInvoicesForPeriod("pas-une-période")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> cron.generateInvoicesForPeriod(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateInvoicesForPeriod_empty_returnsZero() {
        when(query.getResultList()).thenReturn(List.of());
        assertThat(cron.generateInvoicesForPeriod("2026-04")).isZero();
    }

    @Test
    void generateInvoicesForPeriod_oneRow_inserts() {
        when(query.getResultList()).thenReturn(List.<Object[]>of(
            new Object[]{UUID.randomUUID(), "Resto L4", UUID.randomUUID(), new BigDecimal("1000"), new BigDecimal("3")}));
        assertThat(cron.generateInvoicesForPeriod("2026-04")).isEqualTo(1);
    }

    @Test
    void generateMonthlyInvoicesCron_runs() {
        when(query.getResultList()).thenReturn(List.of());
        cron.generateMonthlyInvoicesCron(); // période = mois précédent
    }
}
