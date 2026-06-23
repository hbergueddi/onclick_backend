package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.shared.events.ResourceBookingCreatedEvent;
import com.onesley.oneclick.shared.events.ResourceBookingReminderDueEvent;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires des trous « booking PCC » côté {@link NotificationEventHandler} :
 * <ul>
 *   <li><b>#3</b> — un nouveau booking notifie chaque staff du tenant (in-app + push) ;</li>
 *   <li><b>#4</b> — un rappel de booking crée la notif (metadata anti-doublon) + push au membre.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventHandlerBookingGapTest {

    @Mock NotificationService notificationService;
    @Mock FcmPushService pushService;
    @Mock StaffNotificationPreferenceService staffPrefs;
    @InjectMocks NotificationEventHandler handler;
    @Captor ArgumentCaptor<PushPromoDto> promoCaptor;

    /** P1pref — par défaut toutes catégories ON (aucun filtre staff actif dans ces tests). */
    @BeforeEach
    void enableAllStaffPrefs() {
        when(staffPrefs.isStaffCategoryEnabled(any(), any())).thenReturn(true);
    }

    // ── #3 — nouveau booking → staff notifié ─────────────────────────────────────
    @Test
    void newBooking_notifiesEachStaff_withDashboardLink() {
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID();
        handler.onResourceBookingCreated(new ResourceBookingCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "padel", List.of(s1, s2), Instant.now()));

        verify(notificationService, times(2)).create(any());
        verify(pushService, times(2)).sendPromo(promoCaptor.capture());
        List<UUID> pushed = promoCaptor.getAllValues().stream().flatMap(p -> p.userIds().stream()).toList();
        assertThat(pushed).containsExactlyInAnyOrder(s1, s2);
        assertThat(promoCaptor.getAllValues().get(0).link()).isEqualTo("/prodesk/pcc-bookings");
    }

    @Test
    void newBooking_noStaff_noNotif() {
        handler.onResourceBookingCreated(new ResourceBookingCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "spa", List.of(), Instant.now()));
        verify(notificationService, never()).create(any());
        verify(pushService, never()).sendPromo(any());
    }

    // ── #4 — rappel booking → notif metadata + push membre ───────────────────────
    @Test
    void bookingReminder_createsMetadataNotif_andPushesMember() {
        UUID member = UUID.randomUUID();
        UUID booking = UUID.randomUUID();
        handler.onResourceBookingReminderDue(new ResourceBookingReminderDueEvent(
            member, booking, "h2", "Activité dans 2h", "À tout à l'heure au club !",
            "/pocket/oneclick?tab=suivi", Instant.now()));

        // notif in-app avec metadata bookingId/slot (anti-doublon du cron)
        verify(notificationService).createResourceBookingReminder(
            eq(member), eq("Activité dans 2h"), any(), eq("/pocket/oneclick?tab=suivi"), eq(booking), eq("h2"));
        // push au membre
        verify(pushService).sendPromo(promoCaptor.capture());
        assertThat(promoCaptor.getValue().userIds()).containsExactly(member);
    }
}
