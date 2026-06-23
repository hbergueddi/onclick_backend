package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.shared.events.AnnouncementPublishedEvent;
import com.onesley.oneclick.shared.events.RestaurantRestitutionPaidEvent;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires des Lots <b>B15a</b> (annonce tenant → in-app + push staff) et <b>B6</b>
 * (restitution versée → in-app staff, sans push) côté {@link NotificationEventHandler}.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventHandlerB15B6Test {

    @Mock NotificationService notificationService;
    @Mock FcmPushService pushService;
    @Mock StaffNotificationPreferenceService staffPrefs;
    @InjectMocks NotificationEventHandler handler;

    @Captor ArgumentCaptor<NotificationCreateDto> createCaptor;
    @Captor ArgumentCaptor<PushPromoDto> promoCaptor;

    @BeforeEach
    void enableAllStaffPrefs() {
        when(staffPrefs.isStaffCategoryEnabled(any(), any())).thenReturn(true);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  B15a — annonce publiée → in-app + PUSH staff (le legacy poussait du FCM)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void announcementPublished_notifiesEachStaff_inAppAndPush_announcementType() {
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID();
        handler.onAnnouncementPublished(new AnnouncementPublishedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            List.of(s1, s2), "Fermeture exceptionnelle", "urgent", Instant.now()));

        // in-app : type announcement + lien staff
        verify(notificationService, times(2)).create(createCaptor.capture());
        assertThat(createCaptor.getAllValues()).allSatisfy(n -> {
            assertThat(n.type()).isEqualTo("announcement");
            assertThat(n.link()).isEqualTo("/prodesk/announcements");
            assertThat(n.title()).contains("Annonce urgente");
        });
        // B15a — push FCM pour chaque staff (nouveauté vs in-app only)
        verify(pushService, times(2)).sendPromo(promoCaptor.capture());
        assertThat(promoCaptor.getAllValues().stream().flatMap(p -> p.userIds().stream()).toList())
            .containsExactlyInAnyOrder(s1, s2);
    }

    @Test
    void announcementPublished_permanentPriority_normalPrefix() {
        UUID s1 = UUID.randomUUID();
        handler.onAnnouncementPublished(new AnnouncementPublishedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            List.of(s1), "Nouveaux horaires", "permanent", Instant.now()));
        verify(notificationService).create(createCaptor.capture());
        assertThat(createCaptor.getValue().title()).contains("Nouvelle annonce");
    }

    @Test
    void announcementPublished_noStaff_noNotif() {
        handler.onAnnouncementPublished(new AnnouncementPublishedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(), "x", "urgent", Instant.now()));
        verify(notificationService, never()).create(any());
        verify(pushService, never()).sendPromo(any());

        handler.onAnnouncementPublished(new AnnouncementPublishedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "x", "urgent", Instant.now()));
        verify(notificationService, never()).create(any());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  B6 — restitution versée → in-app staff (SANS push, comme contrat/parrainage)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void restitutionPaid_notifiesEachStaff_inAppOnly_systemType_amountInBody() {
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID();
        handler.onRestaurantRestitutionPaid(new RestaurantRestitutionPaidEvent(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("150.50"), List.of(s1, s2), Instant.now()));

        verify(notificationService, times(2)).create(createCaptor.capture());
        assertThat(createCaptor.getAllValues()).allSatisfy(n -> {
            assertThat(n.type()).isEqualTo("system");
            assertThat(n.title()).isEqualTo("Restitution versée 💸");
            assertThat(n.link()).isEqualTo("/prodesk/oneclick-hi");
            assertThat(n.body()).contains("150.5 MAD");
        });
        // in-app SEUL — aucun push
        verify(pushService, never()).sendPromo(any());
        verify(pushService, never()).sendReservation(any());
    }

    @Test
    void restitutionPaid_nullAmount_genericBody() {
        UUID s1 = UUID.randomUUID();
        handler.onRestaurantRestitutionPaid(new RestaurantRestitutionPaidEvent(
            UUID.randomUUID(), UUID.randomUUID(), null, List.of(s1), Instant.now()));
        verify(notificationService).create(createCaptor.capture());
        assertThat(createCaptor.getValue().body()).contains("Un montant");
    }

    @Test
    void restitutionPaid_noStaff_noNotif() {
        handler.onRestaurantRestitutionPaid(new RestaurantRestitutionPaidEvent(
            UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, List.of(), Instant.now()));
        verify(notificationService, never()).create(any());

        handler.onRestaurantRestitutionPaid(new RestaurantRestitutionPaidEvent(
            UUID.randomUUID(), UUID.randomUUID(), BigDecimal.TEN, null, Instant.now()));
        verify(notificationService, never()).create(any());
    }
}
