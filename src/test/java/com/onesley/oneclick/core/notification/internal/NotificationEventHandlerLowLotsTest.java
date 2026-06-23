package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.shared.events.NoShowPenaltyFinalizedEvent;
import com.onesley.oneclick.shared.events.OfferExpiredEvent;
import com.onesley.oneclick.shared.events.PointsGiftedEvent;
import com.onesley.oneclick.shared.events.PromoRequestReviewedEvent;
import com.onesley.oneclick.shared.events.ReservationGuestRemovedEvent;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires isolés (Mockito) des listeners des 5 lots LOW ajoutés à {@link NotificationEventHandler} :
 * <ul>
 *   <li><b>B14</b> {@code onReservationGuestRemoved} — notif in-app « Invitation annulée » à l'invité
 *       (type {@code reservation}, in-app SEUL), garde sur invitedUserId null ;</li>
 *   <li><b>B12</b> {@code onPromoRequestReviewed} — notif in-app au demandeur (type {@code promotion}),
 *       libellé approuvée/refusée + motif, garde requesterId null ;</li>
 *   <li><b>B11</b> {@code onOfferExpired} — notif in-app au staff filtrée par préférence {@code system}
 *       (in-app SEUL), garde liste vide/null ;</li>
 *   <li><b>B7</b> {@code onPointsGifted} — notif in-app au bénéficiaire (type {@code loyalty}, in-app
 *       SEUL), mention générique si fromName null, garde points &lt;= 0 ;</li>
 *   <li><b>B10</b> {@code onNoShowPenaltyFinalized} — alerte transparence in-app au client via
 *       {@code createNoShowPenaltyFinalAlert} (metadata anti-doublon), garde clientId null.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventHandlerLowLotsTest {

    @Mock NotificationService notificationService;
    @Mock FcmPushService pushService;
    @Mock StaffNotificationPreferenceService staffPrefs;
    @InjectMocks NotificationEventHandler handler;

    @Captor ArgumentCaptor<NotificationCreateDto> createCaptor;

    /** Par défaut toutes catégories staff ON (le filtre est testé à part). */
    @BeforeEach
    void enableAllStaffPrefs() {
        when(staffPrefs.isStaffCategoryEnabled(any(), any())).thenReturn(true);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  B14 — invitation annulée → invité (in-app seul)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void guestRemoved_notifiesInvitedUser_reservationType_inAppOnly() {
        UUID invited = UUID.randomUUID();
        handler.onReservationGuestRemoved(new ReservationGuestRemovedEvent(
            UUID.randomUUID(), invited, null));

        verify(notificationService).create(createCaptor.capture());
        NotificationCreateDto n = createCaptor.getValue();
        assertThat(n.recipientUserId()).isEqualTo(invited);
        assertThat(n.type()).isEqualTo("reservation");
        assertThat(n.title()).isEqualTo("Invitation annulée");
        assertThat(n.body()).contains("L'organisateur");
        verify(pushService, never()).sendPromo(any());
        verify(pushService, never()).sendReservation(any());
    }

    @Test
    void guestRemoved_withOrganizerName_usesIt() {
        handler.onReservationGuestRemoved(new ReservationGuestRemovedEvent(
            UUID.randomUUID(), UUID.randomUUID(), "Adil"));
        verify(notificationService).create(createCaptor.capture());
        assertThat(createCaptor.getValue().body()).contains("Adil");
    }

    @Test
    void guestRemoved_nullInvitedUser_skips() {
        handler.onReservationGuestRemoved(new ReservationGuestRemovedEvent(
            UUID.randomUUID(), null, "Adil"));
        // createInApp(null) garde interne → aucune notif persistée.
        verify(notificationService, never()).create(any());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  B12 — demande promo modérée → demandeur (in-app seul)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void promoReviewed_approved_notifiesRequester_promotionType_inAppOnly() {
        UUID requester = UUID.randomUUID();
        handler.onPromoRequestReviewed(new PromoRequestReviewedEvent(
            UUID.randomUUID(), requester, true, "Happy Hour", null, Instant.now()));

        verify(notificationService).create(createCaptor.capture());
        NotificationCreateDto n = createCaptor.getValue();
        assertThat(n.recipientUserId()).isEqualTo(requester);
        assertThat(n.type()).isEqualTo("promotion");
        assertThat(n.title()).isEqualTo("Demande de promo approuvée ✅");
        assertThat(n.body()).contains("Happy Hour");
        assertThat(n.link()).isEqualTo("/forge/promotions");
        verify(pushService, never()).sendPromo(any());
    }

    @Test
    void promoReviewed_rejected_includesReason() {
        UUID requester = UUID.randomUUID();
        handler.onPromoRequestReviewed(new PromoRequestReviewedEvent(
            UUID.randomUUID(), requester, false, "Promo", "hors charte", Instant.now()));

        verify(notificationService).create(createCaptor.capture());
        NotificationCreateDto n = createCaptor.getValue();
        assertThat(n.title()).isEqualTo("Demande de promo refusée");
        assertThat(n.body()).contains("hors charte");
    }

    @Test
    void promoReviewed_nullRequester_skips() {
        handler.onPromoRequestReviewed(new PromoRequestReviewedEvent(
            UUID.randomUUID(), null, true, "Promo", null, Instant.now()));
        // createInApp(null) garde interne → aucune notif persistée.
        verify(notificationService, never()).create(any());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  B11 — offre expirée → staff (in-app seul, filtre pref system)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void offerExpired_notifiesEachStaff_systemType_inAppOnly() {
        UUID s1 = UUID.randomUUID(), s2 = UUID.randomUUID();
        handler.onOfferExpired(new OfferExpiredEvent(
            UUID.randomUUID(), UUID.randomUUID(), List.of(s1, s2), "Brunch -20%", Instant.now()));

        verify(notificationService, times(2)).create(createCaptor.capture());
        assertThat(createCaptor.getAllValues()).allSatisfy(n -> {
            assertThat(n.type()).isEqualTo("system");
            assertThat(n.title()).isEqualTo("Offre expirée ⌛");
            assertThat(n.body()).contains("Brunch -20%");
            assertThat(n.link()).isEqualTo("/forge/promotions");
        });
        assertThat(createCaptor.getAllValues().stream().map(NotificationCreateDto::recipientUserId).toList())
            .containsExactlyInAnyOrder(s1, s2);
        verify(pushService, never()).sendPromo(any()); // in-app SEUL
    }

    @Test
    void offerExpired_staffPrefOff_skipsThatStaff() {
        UUID s1 = UUID.randomUUID();
        when(staffPrefs.isStaffCategoryEnabled(eq(s1), eq("system"))).thenReturn(false);
        handler.onOfferExpired(new OfferExpiredEvent(
            UUID.randomUUID(), UUID.randomUUID(), List.of(s1), "X", Instant.now()));
        verify(notificationService, never()).create(any());
    }

    @Test
    void offerExpired_emptyOrNullStaff_noNotif() {
        handler.onOfferExpired(new OfferExpiredEvent(
            UUID.randomUUID(), UUID.randomUUID(), List.of(), "X", Instant.now()));
        handler.onOfferExpired(new OfferExpiredEvent(
            UUID.randomUUID(), UUID.randomUUID(), null, "X", Instant.now()));
        verify(notificationService, never()).create(any());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  B7 — don de points → bénéficiaire (in-app seul)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void pointsGifted_notifiesReceiver_loyaltyType_inAppOnly() {
        UUID receiver = UUID.randomUUID();
        handler.onPointsGifted(new PointsGiftedEvent(
            UUID.randomUUID(), receiver, 30, "Sara", Instant.now()));

        verify(notificationService).create(createCaptor.capture());
        NotificationCreateDto n = createCaptor.getValue();
        assertThat(n.recipientUserId()).isEqualTo(receiver);
        assertThat(n.type()).isEqualTo("loyalty");
        assertThat(n.title()).isEqualTo("Cadeau de points 🎁");
        assertThat(n.body()).contains("30").contains("Sara");
        assertThat(n.link()).isEqualTo("/pocket/vault");
        verify(pushService, never()).sendPromo(any());
    }

    @Test
    void pointsGifted_nullFromName_usesGenericMention() {
        handler.onPointsGifted(new PointsGiftedEvent(
            UUID.randomUUID(), UUID.randomUUID(), 10, null, Instant.now()));
        verify(notificationService).create(createCaptor.capture());
        assertThat(createCaptor.getValue().body()).contains("un ami");
    }

    @Test
    void pointsGifted_nonPositivePointsOrNullReceiver_skips() {
        handler.onPointsGifted(new PointsGiftedEvent(UUID.randomUUID(), UUID.randomUUID(), 0, "X", Instant.now()));
        handler.onPointsGifted(new PointsGiftedEvent(UUID.randomUUID(), null, 10, "X", Instant.now()));
        verify(notificationService, never()).create(any());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  B10 — pénalité no-show finalisée → client (transparence in-app, metadata)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    void noShowPenaltyFinalized_callsMetadataAlert_forClient() {
        UUID client = UUID.randomUUID(), resa = UUID.randomUUID();
        handler.onNoShowPenaltyFinalized(new NoShowPenaltyFinalizedEvent(client, resa, Instant.now()));

        verify(notificationService).createNoShowPenaltyFinalAlert(
            eq(client), anyString(), anyString(), eq("/pocket/oneclick?tab=suivi"), eq(resa));
        // pas de notif générique ni de push (transparence in-app dédiée)
        verify(notificationService, never()).create(any());
        verify(pushService, never()).sendPromo(any());
        verify(pushService, never()).sendReservation(any());
    }

    @Test
    void noShowPenaltyFinalized_nullClient_skips() {
        handler.onNoShowPenaltyFinalized(new NoShowPenaltyFinalizedEvent(null, UUID.randomUUID(), Instant.now()));
        verify(notificationService, never()).createNoShowPenaltyFinalAlert(any(), anyString(), anyString(), anyString(), any());
    }
}
