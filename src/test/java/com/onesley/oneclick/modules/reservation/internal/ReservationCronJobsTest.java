package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.shared.events.NoShowPenaltyFinalizedEvent;
import com.onesley.oneclick.shared.events.ReservationReminderDueEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
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
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link ReservationCronJobs} — auto-cancel H-2 (gap #1) + rappels J-1/H-2 (R1).
 *
 * <p>Gap #1 : l'auto-cancel ne fait plus un pur UPDATE bulk muet ; il SELECT les dues, UPDATE,
 * puis publie un {@link ReservationStatusChangedEvent}(reason="auto_expired") par row → le client
 * est notifié (in-app + push) par {@code NotificationEventHandler}. Ces tests verrouillent le
 * fan-out (1 event par row, reason/newStatus corrects, court-circuit si 0 due) et le fait que les
 * rappels publient {@link ReservationReminderDueEvent} (slot/titre corrects).
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class ReservationCronJobsTest {

    @Mock EntityManager em;
    @Mock Query query;
    @Mock ApplicationEventPublisher events;
    @InjectMocks ReservationCronJobs cron;

    @Captor ArgumentCaptor<ReservationReminderDueEvent> reminderCaptor;
    @Captor ArgumentCaptor<ReservationStatusChangedEvent> statusCaptor;
    @Captor ArgumentCaptor<NoShowPenaltyFinalizedEvent> penaltyCaptor;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(cron, "em", em);
        lenient().when(em.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyString(), any())).thenReturn(query); // chaînage UPDATE ... IN (:ids)
        lenient().when(query.executeUpdate()).thenReturn(2);
        lenient().when(query.getResultList()).thenReturn(List.of());
    }

    // ── Gap #1 — auto-cancel notifiant le client ────────────────────────────────
    @Test
    void expireUnanswered_dueRows_updatesAndPublishesAutoExpiredEvent() {
        UUID c1 = UUID.randomUUID(), r1 = UUID.randomUUID(), resto = UUID.randomUUID(), tenant = UUID.randomUUID();
        when(query.getResultList()).thenReturn(List.of(
            new Object[]{r1, c1, resto, tenant, "pending"},
            new Object[]{UUID.randomUUID(), UUID.randomUUID(), resto, tenant, "counter_proposed"}));

        cron.expireUnansweredReservations();

        verify(query).executeUpdate(); // UPDATE exécuté
        verify(events, times(2)).publishEvent(statusCaptor.capture());
        ReservationStatusChangedEvent first = statusCaptor.getAllValues().get(0);
        assertThat(first.reservationId()).isEqualTo(r1);
        assertThat(first.clientId()).isEqualTo(c1);
        assertThat(first.newStatus()).isEqualTo("cancelled");
        assertThat(first.reason()).isEqualTo("auto_expired");
    }

    @Test
    void expireUnanswered_noDueRows_noUpdateNoEvent() {
        when(query.getResultList()).thenReturn(List.of());
        cron.expireUnansweredReservations();
        verify(query, never()).executeUpdate();
        verify(events, never()).publishEvent(any(ReservationStatusChangedEvent.class));
    }

    // ── R1 — rappels J-1 / H-2 ───────────────────────────────────────────────────
    @Test
    void remindersJ1_publishesOneEventPerDueRow_withHourInTitle() {
        UUID client = UUID.randomUUID();
        UUID resa = UUID.randomUUID();
        when(query.getResultList()).thenReturn(List.of(
            new Object[]{client, resa, "19:30"},
            new Object[]{UUID.randomUUID(), UUID.randomUUID(), "21:00"}));

        cron.sendReservationRemindersJ1();

        verify(events, times(2)).publishEvent(reminderCaptor.capture());
        ReservationReminderDueEvent first = reminderCaptor.getAllValues().get(0);
        assertThat(first.recipientUserId()).isEqualTo(client);
        assertThat(first.reservationId()).isEqualTo(resa);
        assertThat(first.slot()).isEqualTo("j1");
        assertThat(first.title()).contains("19:30");
        assertThat(first.link()).isEqualTo("/pocket/oneclick?tab=suivi");
    }

    @Test
    void remindersH2_publishesOneEventPerDueRow_slotH2() {
        UUID client = UUID.randomUUID();
        UUID resa = UUID.randomUUID();
        // singletonList : évite le piège varargs de List.of(Object[]) qui éclaterait la ligne en éléments.
        when(query.getResultList()).thenReturn(java.util.Collections.singletonList(new Object[]{client, resa}));

        cron.sendReservationRemindersH2();

        verify(events).publishEvent(reminderCaptor.capture());
        ReservationReminderDueEvent e = reminderCaptor.getValue();
        assertThat(e.recipientUserId()).isEqualTo(client);
        assertThat(e.reservationId()).isEqualTo(resa);
        assertThat(e.slot()).isEqualTo("h2");
        assertThat(e.title()).isEqualTo("Réservation dans 2h");
    }

    @Test
    void reminders_noDueRows_noEvent() {
        when(query.getResultList()).thenReturn(List.of());
        cron.sendReservationRemindersJ1();
        cron.sendReservationRemindersH2();
        verify(events, never()).publishEvent(any(ReservationReminderDueEvent.class));
    }

    // ── B10 — pénalité no-show finalisée (48h non contestée) ─────────────────────
    @Test
    void finalizeNoShowPenalties_dueRows_publishesOneEventPerRow() {
        UUID c1 = UUID.randomUUID(), r1 = UUID.randomUUID();
        when(query.getResultList()).thenReturn(List.of(
            new Object[]{c1, r1},
            new Object[]{UUID.randomUUID(), UUID.randomUUID()}));

        cron.finalizeNoShowPenalties();

        verify(events, times(2)).publishEvent(penaltyCaptor.capture());
        NoShowPenaltyFinalizedEvent first = penaltyCaptor.getAllValues().get(0);
        assertThat(first.clientId()).isEqualTo(c1);
        assertThat(first.reservationId()).isEqualTo(r1);
    }

    @Test
    void finalizeNoShowPenalties_noDueRows_noEvent() {
        when(query.getResultList()).thenReturn(List.of());
        cron.finalizeNoShowPenalties();
        verify(events, never()).publishEvent(any(NoShowPenaltyFinalizedEvent.class));
    }

    // ── Auto-no_show des confirmées passées (+2h, backlog complet) ────────────────
    @Test
    void autoMarkNoShowPastConfirmed_dueRows_updatesAndPublishesAutoNoShowEvent() {
        UUID c1 = UUID.randomUUID(), r1 = UUID.randomUUID(), resto = UUID.randomUUID(), tenant = UUID.randomUUID();
        when(query.getResultList()).thenReturn(List.of(
            new Object[]{r1, c1, resto, tenant},
            new Object[]{UUID.randomUUID(), UUID.randomUUID(), resto, tenant}));

        cron.autoMarkNoShowPastConfirmed();

        verify(query).executeUpdate(); // UPDATE status='no_show' exécuté
        verify(events, times(2)).publishEvent(statusCaptor.capture());
        ReservationStatusChangedEvent first = statusCaptor.getAllValues().get(0);
        assertThat(first.reservationId()).isEqualTo(r1);
        assertThat(first.clientId()).isEqualTo(c1);
        assertThat(first.oldStatus()).isEqualTo("confirmed");
        assertThat(first.newStatus()).isEqualTo("no_show");
        assertThat(first.reason()).isEqualTo("auto_no_show");
    }

    @Test
    void autoMarkNoShowPastConfirmed_noDueRows_noUpdateNoEvent() {
        when(query.getResultList()).thenReturn(List.of());
        cron.autoMarkNoShowPastConfirmed();
        verify(query, never()).executeUpdate();
        verify(events, never()).publishEvent(any(ReservationStatusChangedEvent.class));
    }

    @Test
    void allCrons_run_queryExecuted() {
        cron.expireUnansweredReservations();
        cron.autoMarkNoShowPastConfirmed();
        cron.sendReservationRemindersJ1();
        cron.sendReservationRemindersH2();
        cron.finalizeNoShowPenalties();
        verify(em, atLeastOnce()).createNativeQuery(anyString());
    }
}
