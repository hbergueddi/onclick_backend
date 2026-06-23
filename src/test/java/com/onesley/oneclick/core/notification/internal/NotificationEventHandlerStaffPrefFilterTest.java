package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.shared.events.AnnouncementPublishedEvent;
import com.onesley.oneclick.shared.events.NoShowDisputeCreatedEvent;
import com.onesley.oneclick.shared.events.ReservationCreatedEvent;
import com.onesley.oneclick.shared.events.ResourceBookingCreatedEvent;
import com.onesley.oneclick.shared.events.RestaurantRestitutionPaidEvent;
import com.onesley.oneclick.shared.events.SeminarRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
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
 * P1pref — tests unitaires du <b>filtre de préférences staff</b> au point des branches staff de
 * {@link NotificationEventHandler}. Vérifie le <b>mapping catégorie ↔ toggle</b> et le SKIP total
 * (ni in-app ni push) quand le toggle est OFF, sans toucher aux notifs CLIENT/MEMBRE.
 *
 * <h3>Mapping catégories ↔ toggles (V85)</h3>
 * <ul>
 *   <li>nouvelle réservation resto / annulation client / contestation no-show → {@code reservation}</li>
 *   <li>nouveau booking club / annulation membre / demande séminaire → {@code booking}</li>
 *   <li>annonce tenant → {@code system}</li>
 *   <li>restitution versée → {@code loyalty}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventHandlerStaffPrefFilterTest {

    @Mock NotificationService notificationService;
    @Mock FcmPushService pushService;
    @Mock StaffNotificationPreferenceService staffPrefs;
    @InjectMocks NotificationEventHandler handler;

    @Captor ArgumentCaptor<NotificationCreateDto> createCaptor;

    // ════════════════════════════════════════════════════════════════════════
    //  Toggle OFF → skip total (in-app + push) — par catégorie
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void reservationCreated_staffReservationOff_skipsStaffEntirely_clientStillNotified() {
        UUID client = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        // catégorie reservation OFF pour le staff
        when(staffPrefs.isStaffCategoryEnabled(eq(staff), eq("reservation"))).thenReturn(false);

        handler.onReservationCreated(new ReservationCreatedEvent(
            UUID.randomUUID(), client, UUID.randomUUID(), UUID.randomUUID(),
            Instant.now(), 2, "pending", List.of(staff)));

        // le CLIENT est toujours notifié (1 in-app + 1 push réservation) ; le STAFF est skippé
        verify(notificationService, times(1)).create(createCaptor.capture());
        assertThat(createCaptor.getValue().recipientUserId()).isEqualTo(client);
        verify(pushService, times(1)).sendReservation(any()); // push client
        verify(pushService, never()).sendPromo(any());        // pas de push staff
    }

    @Test
    void resourceBookingCreated_staffBookingOff_skipsStaff() {
        UUID staff = UUID.randomUUID();
        when(staffPrefs.isStaffCategoryEnabled(eq(staff), eq("booking"))).thenReturn(false);

        handler.onResourceBookingCreated(new ResourceBookingCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "padel", List.of(staff), Instant.now()));

        verify(notificationService, never()).create(any());
        verify(pushService, never()).sendPromo(any());
    }

    @Test
    void seminarRequested_staffBookingOff_skipsStaff() {
        UUID staff = UUID.randomUUID();
        when(staffPrefs.isStaffCategoryEnabled(eq(staff), eq("booking"))).thenReturn(false);

        handler.onSeminarRequested(new SeminarRequestedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(staff), "ACME", Instant.now()));

        verify(notificationService, never()).create(any());
        verify(pushService, never()).sendPromo(any());
    }

    @Test
    void disputeCreated_staffReservationOff_skipsStaff() {
        UUID staff = UUID.randomUUID();
        when(staffPrefs.isStaffCategoryEnabled(eq(staff), eq("reservation"))).thenReturn(false);

        handler.onDisputeCreated(new NoShowDisputeCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            List.of(staff), "Sara K.", Instant.now()));

        verify(notificationService, never()).create(any());
        verify(pushService, never()).sendPromo(any());
    }

    @Test
    void announcementPublished_staffSystemOff_skipsStaff() {
        UUID staff = UUID.randomUUID();
        when(staffPrefs.isStaffCategoryEnabled(eq(staff), eq("system"))).thenReturn(false);

        handler.onAnnouncementPublished(new AnnouncementPublishedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            List.of(staff), "Fermeture", "urgent", Instant.now()));

        verify(notificationService, never()).create(any());
        verify(pushService, never()).sendPromo(any());
    }

    @Test
    void restitutionPaid_staffLoyaltyOff_skipsStaff() {
        UUID staff = UUID.randomUUID();
        when(staffPrefs.isStaffCategoryEnabled(eq(staff), eq("loyalty"))).thenReturn(false);

        handler.onRestaurantRestitutionPaid(new RestaurantRestitutionPaidEvent(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("120.00"), List.of(staff), Instant.now()));

        verify(notificationService, never()).create(any());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Filtre sélectif : un staff OFF skippé, un autre ON notifié
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void resourceBookingCreated_oneStaffOff_oneStaffOn_onlyOnNotified() {
        UUID off = UUID.randomUUID();
        UUID on = UUID.randomUUID();
        when(staffPrefs.isStaffCategoryEnabled(eq(off), eq("booking"))).thenReturn(false);
        when(staffPrefs.isStaffCategoryEnabled(eq(on), eq("booking"))).thenReturn(true);

        handler.onResourceBookingCreated(new ResourceBookingCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "spa", List.of(off, on), Instant.now()));

        // seul le staff ON est notifié (in-app + push)
        verify(notificationService, times(1)).create(createCaptor.capture());
        assertThat(createCaptor.getValue().recipientUserId()).isEqualTo(on);
        verify(pushService, times(1)).sendPromo(any());
    }

    @Test
    void wrongCategory_notConsulted_staffStillNotified() {
        // Le staff a désactivé 'loyalty' mais une nouvelle réservation relève de 'reservation' :
        // la préférence loyalty NE doit PAS bloquer la notif réservation.
        UUID client = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        when(staffPrefs.isStaffCategoryEnabled(eq(staff), eq("reservation"))).thenReturn(true);
        when(staffPrefs.isStaffCategoryEnabled(eq(staff), eq("loyalty"))).thenReturn(false);

        handler.onReservationCreated(new ReservationCreatedEvent(
            UUID.randomUUID(), client, UUID.randomUUID(), UUID.randomUUID(),
            Instant.now(), 2, "pending", List.of(staff)));

        // client + staff notifiés (la cat 'loyalty' OFF est sans effet sur une notif 'reservation')
        verify(notificationService, times(2)).create(any());
        verify(pushService, times(1)).sendPromo(any()); // push staff
    }
}
