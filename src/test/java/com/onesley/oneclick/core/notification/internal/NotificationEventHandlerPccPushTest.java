package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.shared.events.ResourceBookingStatusChangedEvent;
import com.onesley.oneclick.shared.events.SeminarRequestedEvent;
import com.onesley.oneclick.shared.events.SeminarStatusChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires R3 — push PCC : changement de statut booking → client organisateur, et
 * séminaires (demande → staff, statut → membre). Parité legacy {@code notifyPccClient}/{@code notifyPccStaff}.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventHandlerPccPushTest {

    @Mock NotificationService notificationService;
    @Mock FcmPushService pushService;
    @Mock StaffNotificationPreferenceService staffPrefs;
    @InjectMocks NotificationEventHandler handler;
    @Captor ArgumentCaptor<PushPromoDto> pushCaptor;

    /** P1pref — par défaut toutes catégories ON (aucun filtre staff actif dans ces tests). */
    @BeforeEach
    void enableAllStaffPrefs() {
        when(staffPrefs.isStaffCategoryEnabled(any(), any())).thenReturn(true);
    }

    private ResourceBookingStatusChangedEvent booking(UUID organizer, UUID changedBy, String newStatus) {
        return new ResourceBookingStatusChangedEvent(
            UUID.randomUUID(), organizer, UUID.randomUUID(), UUID.randomUUID(),
            "padel", "pending", newStatus, changedBy, Instant.now());
    }

    @Test
    void bookingConfirmed_createsInApp_andPushesOrganizer() {
        UUID organizer = UUID.randomUUID();
        handler.onResourceBookingStatusChanged(booking(organizer, UUID.randomUUID(), "confirmed"));
        verify(notificationService).create(any(NotificationCreateDto.class));
        verify(pushService).sendPromo(pushCaptor.capture());
        assertThat(pushCaptor.getValue().userIds()).containsExactly(organizer);
        assertThat(pushCaptor.getValue().title()).contains("confirmée");
    }

    @Test
    void bookingCompleted_pushesHonoredMessage() {
        UUID organizer = UUID.randomUUID();
        handler.onResourceBookingStatusChanged(booking(organizer, UUID.randomUUID(), "completed"));
        verify(pushService).sendPromo(pushCaptor.capture());
        assertThat(pushCaptor.getValue().title()).contains("Merci");
    }

    @Test
    void bookingPending_noPush_noInApp() {
        handler.onResourceBookingStatusChanged(booking(UUID.randomUUID(), UUID.randomUUID(), "pending"));
        verify(pushService, never()).sendPromo(any());
        verify(notificationService, never()).create(any());
    }

    @Test
    void bookingCancelledByStaff_pushesClient() {
        UUID organizer = UUID.randomUUID();
        // changedBy ≠ organizer → annulation par un tiers (staff/admin) → push client
        handler.onResourceBookingStatusChanged(booking(organizer, UUID.randomUUID(), "cancelled"));
        verify(notificationService).create(any(NotificationCreateDto.class));
        verify(pushService).sendPromo(pushCaptor.capture());
        assertThat(pushCaptor.getValue().userIds()).containsExactly(organizer);
        assertThat(pushCaptor.getValue().title()).contains("annulée");
    }

    @Test
    void bookingCancelledByMember_noPush() {
        UUID organizer = UUID.randomUUID();
        // changedBy == organizer → auto-annulation du membre → aucun push à lui-même
        handler.onResourceBookingStatusChanged(booking(organizer, organizer, "cancelled"));
        verify(pushService, never()).sendPromo(any());
        verify(notificationService, never()).create(any());
    }

    @Test
    void seminarRequested_pushesEachStaffRecipient() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        handler.onSeminarRequested(new SeminarRequestedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(s1, s2), "ACME Corp", Instant.now()));
        verify(pushService, times(2)).sendPromo(any());
    }

    @Test
    void seminarStatusChanged_pushesOrganizerMember() {
        UUID organizer = UUID.randomUUID();
        handler.onSeminarStatusChanged(new SeminarStatusChangedEvent(
            UUID.randomUUID(), organizer, UUID.randomUUID(), "confirmee", "ACME Corp", Instant.now()));
        verify(pushService).sendPromo(pushCaptor.capture());
        assertThat(pushCaptor.getValue().userIds()).containsExactly(organizer);
    }
}
