package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.shared.events.ReservationCreatedEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
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
 * Tests unitaires des deux trous « réservation » côté {@link NotificationEventHandler} :
 * <ul>
 *   <li><b>#2</b> — une nouvelle réservation notifie le client <i>et</i> chaque staff du resto
 *       (in-app + push), destinataires portés sur l'event ;</li>
 *   <li><b>#1</b> — une annulation auto (reason="auto_expired") produit un message client dédié
 *       (≠ annulation manuelle).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventHandlerReservationGapTest {

    @Mock NotificationService notificationService;
    @Mock FcmPushService pushService;
    @Mock StaffNotificationPreferenceService staffPrefs;
    @InjectMocks NotificationEventHandler handler;

    @Captor ArgumentCaptor<NotificationCreateDto> createCaptor;
    @Captor ArgumentCaptor<PushPromoDto> promoCaptor;

    /** P1pref — par défaut toutes catégories ON (aucun filtre staff actif dans ces tests). */
    @BeforeEach
    void enableAllStaffPrefs() {
        when(staffPrefs.isStaffCategoryEnabled(any(), any())).thenReturn(true);
    }

    private static ReservationCreatedEvent created(UUID clientId, List<UUID> staff) {
        return new ReservationCreatedEvent(
            UUID.randomUUID(), clientId, UUID.randomUUID(), UUID.randomUUID(),
            Instant.now(), 2, "pending", staff);
    }

    // ── #2 — nouvelle réservation → staff notifié ────────────────────────────────
    @Test
    void newReservation_notifiesClientAndEachStaff() {
        UUID client = UUID.randomUUID();
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID();

        handler.onReservationCreated(created(client, List.of(s1, s2)));

        // 1 notif in-app client + 1 par staff = 3 ; push : 1 résa (client) + 2 promo (staff)
        verify(notificationService, times(3)).create(createCaptor.capture());
        verify(pushService, times(1)).sendReservation(org.mockito.ArgumentMatchers.any());
        verify(pushService, times(2)).sendPromo(promoCaptor.capture());

        // Les push staff ciblent bien s1 et s2 et pointent vers l'agenda staff.
        List<UUID> pushedStaff = promoCaptor.getAllValues().stream()
            .flatMap(p -> p.userIds().stream()).toList();
        assertThat(pushedStaff).containsExactlyInAnyOrder(s1, s2);
        assertThat(promoCaptor.getAllValues().get(0).link()).isEqualTo("/prodesk/calendrier");
    }

    @Test
    void newReservation_noStaff_onlyClientNotified() {
        UUID client = UUID.randomUUID();
        handler.onReservationCreated(created(client, null));
        verify(notificationService, times(1)).create(org.mockito.ArgumentMatchers.any());
        verify(pushService, times(1)).sendReservation(org.mockito.ArgumentMatchers.any());
        verify(pushService, never()).sendPromo(org.mockito.ArgumentMatchers.any());
    }

    // ── #1 — annulation auto vs manuelle (message client distinct) ────────────────
    @Test
    void autoExpiredCancellation_usesDedicatedClientMessage() {
        UUID client = UUID.randomUUID();
        handler.onReservationStatusChanged(new ReservationStatusChangedEvent(
            UUID.randomUUID(), client, UUID.randomUUID(), UUID.randomUUID(),
            "pending", "cancelled", "auto_expired", Instant.now()));

        verify(notificationService).create(createCaptor.capture());
        assertThat(createCaptor.getValue().title()).isEqualTo("Réservation expirée");
        assertThat(createCaptor.getValue().body()).contains("n'a pas été confirmée à temps");
    }

    @Test
    void manualCancellation_usesGenericClientMessage() {
        UUID client = UUID.randomUUID();
        handler.onReservationStatusChanged(new ReservationStatusChangedEvent(
            UUID.randomUUID(), client, UUID.randomUUID(), UUID.randomUUID(),
            "confirmed", "cancelled", null, Instant.now()));

        verify(notificationService).create(createCaptor.capture());
        assertThat(createCaptor.getValue().title()).isEqualTo("Réservation annulée");
    }
}
