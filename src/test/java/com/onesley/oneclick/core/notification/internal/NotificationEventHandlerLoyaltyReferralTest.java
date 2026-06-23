package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.shared.events.LoyaltyEarnedEvent;
import com.onesley.oneclick.shared.events.RestaurantReferralActivatedEvent;
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

/**
 * Tests unitaires isolés (Mockito — {@code NotificationService} / {@code FcmPushService} mockés) des
 * listeners {@link NotificationEventHandler} pour :
 * <ul>
 *   <li><b>Lot B3</b> — {@code onRestaurantReferralActivated} : notif in-app {@code system} à chaque
 *       admin plateforme, in-app SEUL (pas de push) ;</li>
 *   <li><b>Lot B2</b> — {@code onLoyaltyEarned} : notif in-app {@code loyalty} « points gagnés » au
 *       client UNIQUEMENT pour un vrai scan ({@code reason} préfixé {@code "snap2earn|"}), in-app
 *       SEUL, avec exclusion des autres {@code reason} (referral/gift/welcome/technique) et des
 *       montants {@code <= 0}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventHandlerLoyaltyReferralTest {

    @Mock NotificationService notificationService;
    @Mock FcmPushService pushService;
    @InjectMocks NotificationEventHandler handler;

    @Captor ArgumentCaptor<NotificationCreateDto> createCaptor;

    private RestaurantReferralActivatedEvent referralEvent(List<UUID> admins) {
        return new RestaurantReferralActivatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100, UUID.randomUUID(),
            admins, Instant.now());
    }

    private LoyaltyEarnedEvent earnedEvent(UUID clientId, int points, String reason) {
        return new LoyaltyEarnedEvent(
            UUID.randomUUID(), UUID.randomUUID(), clientId, UUID.randomUUID(), UUID.randomUUID(),
            points, new BigDecimal("250.00"), reason, Instant.now());
    }

    // ── Lot B3 — parrainage resto→resto activé → admins (in-app seul) ────────────────────────────

    @Test
    void referralActivated_notifiesEachAdmin_systemType_inAppOnly() {
        UUID a1 = UUID.randomUUID(), a2 = UUID.randomUUID();
        handler.onRestaurantReferralActivated(referralEvent(List.of(a1, a2)));

        verify(notificationService, times(2)).create(createCaptor.capture());
        List<UUID> recipients = createCaptor.getAllValues().stream()
            .map(NotificationCreateDto::recipientUserId).toList();
        assertThat(recipients).containsExactlyInAnyOrder(a1, a2);
        NotificationCreateDto first = createCaptor.getAllValues().get(0);
        assertThat(first.type()).isEqualTo("system");
        assertThat(first.title()).isEqualTo("Nouveau parrainage restaurant 🤝");
        assertThat(first.body()).contains("100 points");
        assertThat(first.link()).isEqualTo("/galaxy/contrats");
        // In-app SEUL : aucun push admin (parité legacy admin_notifications).
        verify(pushService, never()).sendPromo(any());
    }

    @Test
    void referralActivated_noAdmins_noNotif() {
        handler.onRestaurantReferralActivated(referralEvent(List.of()));
        verify(notificationService, never()).create(any());
        verify(pushService, never()).sendPromo(any());
    }

    @Test
    void referralActivated_nullAdmins_noNotif() {
        handler.onRestaurantReferralActivated(referralEvent(null));
        verify(notificationService, never()).create(any());
    }

    // ── Lot B2 — points gagnés au scan → client (in-app seul, filtré snap2earn) ──────────────────

    @Test
    void loyaltyEarned_snap2earn_notifiesClient_loyaltyType_inAppOnly() {
        UUID client = UUID.randomUUID();
        handler.onLoyaltyEarned(earnedEvent(client, 25, "snap2earn|TICKET-42|https://photo"));

        verify(notificationService).create(createCaptor.capture());
        NotificationCreateDto dto = createCaptor.getValue();
        assertThat(dto.recipientUserId()).isEqualTo(client);
        assertThat(dto.type()).isEqualTo("loyalty");
        assertThat(dto.title()).isEqualTo("Points gagnés 🎉");
        assertThat(dto.body()).contains("+25 points");
        assertThat(dto.link()).isEqualTo("/pocket/vault");
        // In-app SEUL (parité legacy) : pas de push.
        verify(pushService, never()).sendPromo(any());
        verify(pushService, never()).sendReservation(any());
    }

    @Test
    void loyaltyEarned_snap2earn_emptyTicketRef_stillNotifies() {
        UUID client = UUID.randomUUID();
        // snap2earn sans ticketRef ni photo → reason = "snap2earn||"
        handler.onLoyaltyEarned(earnedEvent(client, 10, "snap2earn||"));
        verify(notificationService).create(any());
    }

    @Test
    void loyaltyEarned_restaurantReferralReason_excluded() {
        handler.onLoyaltyEarned(earnedEvent(UUID.randomUUID(), 100, "RESTAURANT_REFERRAL"));
        verify(notificationService, never()).create(any());
    }

    @Test
    void loyaltyEarned_giftReason_excluded() {
        handler.onLoyaltyEarned(earnedEvent(UUID.randomUUID(), 50, "gift:from:" + UUID.randomUUID() + " | merci"));
        verify(notificationService, never()).create(any());
    }

    @Test
    void loyaltyEarned_welcomeReason_excluded() {
        handler.onLoyaltyEarned(earnedEvent(UUID.randomUUID(), 30, "welcome"));
        verify(notificationService, never()).create(any());
    }

    @Test
    void loyaltyEarned_nullReason_excluded() {
        handler.onLoyaltyEarned(earnedEvent(UUID.randomUUID(), 30, null));
        verify(notificationService, never()).create(any());
    }

    @Test
    void loyaltyEarned_zeroOrNegativePoints_excluded() {
        handler.onLoyaltyEarned(earnedEvent(UUID.randomUUID(), 0, "snap2earn|T|"));
        handler.onLoyaltyEarned(earnedEvent(UUID.randomUUID(), -5, "snap2earn|T|"));
        verify(notificationService, never()).create(any());
    }

    @Test
    void loyaltyEarned_nullClient_excluded() {
        handler.onLoyaltyEarned(earnedEvent(null, 20, "snap2earn|T|"));
        verify(notificationService, never()).create(any());
    }
}
