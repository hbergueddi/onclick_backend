package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.shared.events.NoShowDisputeCreatedEvent;
import com.onesley.oneclick.shared.events.ReferralActivatedEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
import com.onesley.oneclick.shared.events.ResourceBookingStatusChangedEvent;
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
 * Tests unitaires isolés (Mockito — {@code NotificationService}/{@code FcmPushService} mockés) des
 * listeners ajoutés aux Lots B1 / B8 / B9 côté {@link NotificationEventHandler} :
 * <ul>
 *   <li><b>B1</b> {@code onReferralActivated} — notif in-app au PARRAIN <i>et</i> au FILLEUL
 *       (type {@code loyalty}, deep-link Vault), titres distincts, in-app SEUL (pas de push),
 *       garde sur les ids null ;</li>
 *   <li><b>B8</b> {@code onDisputeCreated} — notif in-app + push à chaque staff du resto
 *       (type {@code reservation}, lien calendrier staff), garde liste vide/null ;</li>
 *   <li><b>B9 résa</b> {@code onReservationStatusChanged} — sur annulation, notifie le staff
 *       (in-app + push) uniquement si {@code staffRecipientIds} porté (= annulation par le client) ;
 *       sinon (staff/système : liste null) seul le client est notifié ;</li>
 *   <li><b>B9 booking</b> {@code onResourceBookingStatusChanged} — sur auto-annulation du membre
 *       ({@code changedBy == organizerId}), pas de push au membre mais notif staff ; annulation
 *       par un tiers → push au client (comportement existant préservé).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventHandlerB1B8B9Test {

    @Mock NotificationService notificationService;
    @Mock FcmPushService pushService;
    @Mock StaffNotificationPreferenceService staffPrefs;
    @InjectMocks NotificationEventHandler handler;

    @Captor ArgumentCaptor<NotificationCreateDto> createCaptor;
    @Captor ArgumentCaptor<PushPromoDto> promoCaptor;

    /** P1pref — par défaut toutes catégories ON (le filtre staff est testé séparément). */
    @BeforeEach
    void enableAllStaffPrefs() {
        when(staffPrefs.isStaffCategoryEnabled(any(), any())).thenReturn(true);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  B1 — parrainage client activé → parrain + filleul (in-app seul)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void referralActivated_notifiesReferrerAndReferred_loyaltyType_inAppOnly() {
        UUID referrer = UUID.randomUUID(), referred = UUID.randomUUID();
        handler.onReferralActivated(new ReferralActivatedEvent(
            UUID.randomUUID(), referrer, referred, 50, Instant.now()));

        verify(notificationService, times(2)).create(createCaptor.capture());
        List<NotificationCreateDto> notifs = createCaptor.getAllValues();
        // recipients
        assertThat(notifs.stream().map(NotificationCreateDto::recipientUserId).toList())
            .containsExactlyInAnyOrder(referrer, referred);
        // type + lien Vault sur les deux
        assertThat(notifs).allSatisfy(n -> {
            assertThat(n.type()).isEqualTo("loyalty");
            assertThat(n.link()).isEqualTo("/pocket/vault");
            assertThat(n.body()).contains("50");
        });
        // titres distincts parrain vs filleul
        NotificationCreateDto referrerNotif = notifs.stream()
            .filter(n -> n.recipientUserId().equals(referrer)).findFirst().orElseThrow();
        NotificationCreateDto referredNotif = notifs.stream()
            .filter(n -> n.recipientUserId().equals(referred)).findFirst().orElseThrow();
        assertThat(referrerNotif.title()).isEqualTo("Parrainage réussi 🎉 +50 pts");
        assertThat(referredNotif.title()).isEqualTo("Bienvenue ! +50 pts offerts");
        // in-app SEUL
        verify(pushService, never()).sendPromo(any());
        verify(pushService, never()).sendReservation(any());
    }

    @Test
    void referralActivated_nullReferrer_skipsReferrerNotifiesReferred() {
        UUID referred = UUID.randomUUID();
        handler.onReferralActivated(new ReferralActivatedEvent(
            UUID.randomUUID(), null, referred, 50, Instant.now()));
        // createInApp(null) skip → 1 seule notif (le filleul)
        verify(notificationService, times(1)).create(createCaptor.capture());
        assertThat(createCaptor.getValue().recipientUserId()).isEqualTo(referred);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  B8 — contestation no-show créée → staff (in-app + push)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void disputeCreated_notifiesEachStaff_inAppAndPush_calendarLink() {
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID();
        handler.onDisputeCreated(new NoShowDisputeCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            List.of(s1, s2), "Sara K.", Instant.now()));

        verify(notificationService, times(2)).create(createCaptor.capture());
        assertThat(createCaptor.getAllValues()).allSatisfy(n -> {
            assertThat(n.type()).isEqualTo("reservation");
            assertThat(n.title()).isEqualTo("Contestation no-show ⚖️");
            assertThat(n.body()).contains("Sara K.");
            assertThat(n.link()).isEqualTo("/prodesk/calendrier");
        });
        verify(pushService, times(2)).sendPromo(promoCaptor.capture());
        assertThat(promoCaptor.getAllValues().stream().flatMap(p -> p.userIds().stream()).toList())
            .containsExactlyInAnyOrder(s1, s2);
    }

    @Test
    void disputeCreated_nullClientName_usesGenericMention() {
        UUID s1 = UUID.randomUUID();
        handler.onDisputeCreated(new NoShowDisputeCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            List.of(s1), null, Instant.now()));
        verify(notificationService).create(createCaptor.capture());
        assertThat(createCaptor.getValue().body()).contains("Un·e client·e");
    }

    @Test
    void disputeCreated_noStaff_noNotif() {
        handler.onDisputeCreated(new NoShowDisputeCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(), "X", Instant.now()));
        verify(notificationService, never()).create(any());
        verify(pushService, never()).sendPromo(any());

        handler.onDisputeCreated(new NoShowDisputeCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, "X", Instant.now()));
        verify(notificationService, never()).create(any());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  B9 résa — annulation par le client → staff notifié ; sinon non
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void reservationCancelledByClient_notifiesClientAndStaff() {
        UUID client = UUID.randomUUID();
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID();
        handler.onReservationStatusChanged(new ReservationStatusChangedEvent(
            UUID.randomUUID(), client, UUID.randomUUID(), UUID.randomUUID(),
            "confirmed", "cancelled", null,
            client,                  // changedBy = le client
            List.of(s1, s2),         // staff porté = annulation par le client
            Instant.now()));

        // client : notif in-app + push réservation
        verify(notificationService, times(3)).create(createCaptor.capture()); // 1 client + 2 staff
        verify(pushService, times(1)).sendReservation(any());                 // push client
        verify(pushService, times(2)).sendPromo(promoCaptor.capture());       // push staff
        // staff visés + lien calendrier
        assertThat(promoCaptor.getAllValues().stream().flatMap(p -> p.userIds().stream()).toList())
            .containsExactlyInAnyOrder(s1, s2);
        // un des createInApp staff porte le titre dédié
        assertThat(createCaptor.getAllValues().stream().map(NotificationCreateDto::title).toList())
            .contains("Réservation annulée par le client 🔔");
    }

    @Test
    void reservationCancelledByStaff_onlyClientNotified_noStaffBranch() {
        UUID client = UUID.randomUUID();
        handler.onReservationStatusChanged(new ReservationStatusChangedEvent(
            UUID.randomUUID(), client, UUID.randomUUID(), UUID.randomUUID(),
            "confirmed", "cancelled", null,
            UUID.randomUUID(),       // changedBy = staff
            null,                    // staff non porté (annulation par le staff)
            Instant.now()));

        // seul le client : 1 in-app + 1 push réservation, aucun push staff
        verify(notificationService, times(1)).create(any());
        verify(pushService, times(1)).sendReservation(any());
        verify(pushService, never()).sendPromo(any());
    }

    @Test
    void reservationAutoExpired_onlyClient_dedicatedMessage_noStaff() {
        UUID client = UUID.randomUUID();
        // compat constructor (cron) : changedBy/staff null → message expiré, pas de staff
        handler.onReservationStatusChanged(new ReservationStatusChangedEvent(
            UUID.randomUUID(), client, UUID.randomUUID(), UUID.randomUUID(),
            "pending", "cancelled", "auto_expired", Instant.now()));

        verify(notificationService, times(1)).create(createCaptor.capture());
        assertThat(createCaptor.getValue().title()).isEqualTo("Réservation expirée");
        verify(pushService, never()).sendPromo(any());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  B9 booking — membre s'auto-annule → staff ; tiers → client
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void bookingCancelledByMember_noPushToMember_notifiesStaff() {
        UUID member = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        handler.onResourceBookingStatusChanged(new ResourceBookingStatusChangedEvent(
            UUID.randomUUID(), member, UUID.randomUUID(), UUID.randomUUID(), "padel",
            "confirmed", "cancelled",
            member,                  // changedBy == organizer → auto-annulation
            List.of(s1),
            Instant.now()));

        // staff notifié, membre PAS poussé
        verify(notificationService, times(1)).create(createCaptor.capture());
        assertThat(createCaptor.getValue().recipientUserId()).isEqualTo(s1);
        assertThat(createCaptor.getValue().link()).isEqualTo("/prodesk/pcc-bookings");
        verify(pushService, times(1)).sendPromo(promoCaptor.capture());
        assertThat(promoCaptor.getValue().userIds()).containsExactly(s1);
    }

    @Test
    void bookingCancelledByMember_noStaffRecipients_noNotif() {
        UUID member = UUID.randomUUID();
        handler.onResourceBookingStatusChanged(new ResourceBookingStatusChangedEvent(
            UUID.randomUUID(), member, UUID.randomUUID(), UUID.randomUUID(), "spa",
            "confirmed", "cancelled", member, List.of(), Instant.now()));
        verify(notificationService, never()).create(any());
        verify(pushService, never()).sendPromo(any());
    }

    @Test
    void bookingCancelledByStaff_pushesClient_notMemberBranch() {
        UUID member = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        // compat constructor (staff/admin cancel) : pas de staffRecipientIds → branche client
        handler.onResourceBookingStatusChanged(new ResourceBookingStatusChangedEvent(
            UUID.randomUUID(), member, UUID.randomUUID(), UUID.randomUUID(), "golf",
            "confirmed", "cancelled", staff, Instant.now()));

        verify(notificationService, times(1)).create(createCaptor.capture());
        assertThat(createCaptor.getValue().recipientUserId()).isEqualTo(member);
        assertThat(createCaptor.getValue().title()).isEqualTo("Réservation annulée");
        verify(pushService, times(1)).sendPromo(any()); // push client
    }
}
