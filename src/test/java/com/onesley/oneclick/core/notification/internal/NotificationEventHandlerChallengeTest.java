package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.shared.events.NoShowDisputeResolvedEvent;
import com.onesley.oneclick.shared.events.OfferCreatedEvent;
import com.onesley.oneclick.shared.events.TierReachedEvent;
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

/**
 * Tests unitaires des candidats push « net-new » (challenge) côté {@link NotificationEventHandler} :
 * <ul>
 *   <li>CH-2 — contestation no-show résolue (acceptée/refusée) → client ;</li>
 *   <li>CH-1 — nouvelle offre d'un resto favori → favoriteurs ;</li>
 *   <li>CH-3 — montée de palier fidélité → client.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventHandlerChallengeTest {

    @Mock NotificationService notificationService;
    @Mock FcmPushService pushService;
    @InjectMocks NotificationEventHandler handler;

    @Captor ArgumentCaptor<NotificationCreateDto> createCaptor;
    @Captor ArgumentCaptor<PushPromoDto> promoCaptor;

    // ── CH-2 — dispute résolue ────────────────────────────────────────────────────
    @Test
    void disputeAccepted_notifiesClient_penaltyRemoved() {
        UUID client = UUID.randomUUID();
        handler.onDisputeResolved(new NoShowDisputeResolvedEvent(
            UUID.randomUUID(), UUID.randomUUID(), client, UUID.randomUUID(), true));
        verify(notificationService).create(createCaptor.capture());
        assertThat(createCaptor.getValue().title()).isEqualTo("Contestation acceptée ✅");
        verify(pushService).sendPromo(promoCaptor.capture());
        assertThat(promoCaptor.getValue().userIds()).containsExactly(client);
    }

    @Test
    void disputeRefused_notifiesClient_penaltyKept() {
        UUID client = UUID.randomUUID();
        handler.onDisputeResolved(new NoShowDisputeResolvedEvent(
            UUID.randomUUID(), UUID.randomUUID(), client, UUID.randomUUID(), false));
        verify(notificationService).create(createCaptor.capture());
        assertThat(createCaptor.getValue().title()).isEqualTo("Contestation refusée");
    }

    @Test
    void disputeNullClient_noNotif() {
        handler.onDisputeResolved(new NoShowDisputeResolvedEvent(
            UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), true));
        verify(notificationService, never()).create(any());
        verify(pushService, never()).sendPromo(any());
    }

    // ── CH-1 — nouvelle offre → favoriteurs ───────────────────────────────────────
    @Test
    void newOffer_notifiesEachFavoriter_promosLink() {
        UUID f1 = UUID.randomUUID(), f2 = UUID.randomUUID();
        handler.onOfferCreated(new OfferCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "-20% ce week-end",
            "desc", Instant.now(), Instant.now().plusSeconds(86400), null, null, List.of(f1, f2)));
        verify(notificationService, times(2)).create(any());
        verify(pushService, times(2)).sendPromo(promoCaptor.capture());
        List<UUID> pushed = promoCaptor.getAllValues().stream().flatMap(p -> p.userIds().stream()).toList();
        assertThat(pushed).containsExactlyInAnyOrder(f1, f2);
        assertThat(promoCaptor.getAllValues().get(0).link()).isEqualTo("/pocket/promos");
    }

    @Test
    void newOffer_noFavoriters_noNotif() {
        handler.onOfferCreated(new OfferCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "t",
            "d", Instant.now(), Instant.now().plusSeconds(60), null, null, List.of()));
        verify(notificationService, never()).create(any());
        verify(pushService, never()).sendPromo(any());
    }

    // ── CH-3 — montée de palier ───────────────────────────────────────────────────
    @Test
    void tierReached_notifiesClientWithTierName() {
        UUID client = UUID.randomUUID();
        handler.onTierReached(new TierReachedEvent(client, UUID.randomUUID(), "Sapphire", 1800, Instant.now()));
        verify(notificationService).create(createCaptor.capture());
        assertThat(createCaptor.getValue().title()).isEqualTo("Nouveau palier atteint 🎉");
        assertThat(createCaptor.getValue().body()).contains("Sapphire");
        verify(pushService).sendPromo(promoCaptor.capture());
        assertThat(promoCaptor.getValue().userIds()).containsExactly(client);
    }
}
