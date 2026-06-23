package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.shared.events.ResourceBookingReminderDueEvent;
import com.onesley.oneclick.shared.events.ResourceBookingStatusChangedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link ResourceBookingCronJobs} — auto-cancel notifiant (gap #5) +
 * rappels J-1/H-2 (gap #4). Verrouille le fan-out (1 event par row, reason/slot corrects,
 * court-circuit si 0 due) et le {@code changedBy=null} de l'annulation système.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class ResourceBookingCronJobsTest {

    @Mock EntityManager em;
    @Mock Query query;
    @Mock ApplicationEventPublisher events;
    @InjectMocks ResourceBookingCronJobs cron;

    @Captor ArgumentCaptor<ResourceBookingStatusChangedEvent> statusCaptor;
    @Captor ArgumentCaptor<ResourceBookingReminderDueEvent> reminderCaptor;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(cron, "em", em);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query);
        lenient().when(query.executeUpdate()).thenReturn(1);
        lenient().when(query.getResultList()).thenReturn(List.of());
    }

    // ── #5 — auto-cancel notifiant ───────────────────────────────────────────────
    @Test
    void expire_dueBookings_updatesAndPublishesSystemCancellation() {
        UUID b1 = UUID.randomUUID(), org = UUID.randomUUID(), res = UUID.randomUUID(), tenant = UUID.randomUUID();
        when(query.getResultList()).thenReturn(java.util.Collections.singletonList(
            new Object[]{b1, org, res, tenant, "padel", "pending"}));

        cron.expireUnansweredResourceBookings();

        verify(query).executeUpdate();
        verify(events).publishEvent(statusCaptor.capture());
        ResourceBookingStatusChangedEvent e = statusCaptor.getValue();
        assertThat(e.bookingId()).isEqualTo(b1);
        assertThat(e.organizerId()).isEqualTo(org);
        assertThat(e.newStatus()).isEqualTo("cancelled");
        assertThat(e.changedBy()).as("annulation système → changedBy null → push client (pas de skip)").isNull();
    }

    @Test
    void expire_noDue_noUpdateNoEvent() {
        when(query.getResultList()).thenReturn(List.of());
        cron.expireUnansweredResourceBookings();
        verify(query, never()).executeUpdate();
        verify(events, never()).publishEvent(any(ResourceBookingStatusChangedEvent.class));
    }

    // ── #4 — rappels J-1 / H-2 ───────────────────────────────────────────────────
    @Test
    void remindersJ1_publishesPerRow_slotJ1_withHour() {
        UUID org = UUID.randomUUID(), b = UUID.randomUUID();
        when(query.getResultList()).thenReturn(java.util.Collections.singletonList(new Object[]{org, b, "10:30"}));
        cron.sendResourceBookingRemindersJ1();
        verify(events).publishEvent(reminderCaptor.capture());
        ResourceBookingReminderDueEvent e = reminderCaptor.getValue();
        assertThat(e.recipientUserId()).isEqualTo(org);
        assertThat(e.bookingId()).isEqualTo(b);
        assertThat(e.slot()).isEqualTo("j1");
        assertThat(e.title()).contains("10:30");
        assertThat(e.link()).isEqualTo("/pocket/oneclick?tab=suivi");
    }

    @Test
    void remindersH2_publishesPerRow_slotH2() {
        UUID org = UUID.randomUUID(), b = UUID.randomUUID();
        when(query.getResultList()).thenReturn(java.util.Collections.singletonList(new Object[]{org, b}));
        cron.sendResourceBookingRemindersH2();
        verify(events).publishEvent(reminderCaptor.capture());
        ResourceBookingReminderDueEvent e = reminderCaptor.getValue();
        assertThat(e.slot()).isEqualTo("h2");
        assertThat(e.title()).isEqualTo("Activité dans 2h");
    }

    @Test
    void reminders_noDue_noEvent() {
        when(query.getResultList()).thenReturn(List.of());
        cron.sendResourceBookingRemindersJ1();
        cron.sendResourceBookingRemindersH2();
        verify(events, never()).publishEvent(any(ResourceBookingReminderDueEvent.class));
    }
}
