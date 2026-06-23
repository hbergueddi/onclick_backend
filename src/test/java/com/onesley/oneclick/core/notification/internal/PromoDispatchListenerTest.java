package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushResultDto;
import com.onesley.oneclick.shared.events.PromoAudienceResolvedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link PromoDispatchListener} — push fan-out + markSent, cas 0 destinataire,
 * et <b>B15b</b> : une ligne in-app ({@code type='promotion'}) créée par user du segment, en plus
 * du push.
 */
@ExtendWith(MockitoExtension.class)
class PromoDispatchListenerTest {

    @Mock FcmPushService pushService;
    @Mock PromoNotificationService promoService;
    @Mock NotificationService notificationService;
    @InjectMocks PromoDispatchListener listener;
    @Captor ArgumentCaptor<PushPromoDto> dtoCaptor;
    @Captor ArgumentCaptor<NotificationCreateDto> inAppCaptor;

    @Test
    void withRecipients_allSent_pushesAndMarksSentNoError() {
        UUID req = UUID.randomUUID();
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(pushService.sendPromo(any())).thenReturn(new PushResultDto(2, 2, List.of(), "ok"));

        listener.onPromoAudienceResolved(new PromoAudienceResolvedEvent(req, ids, "T", "B", "/l", Instant.now()));

        verify(pushService).sendPromo(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().userIds()).isEqualTo(ids);
        assertThat(dtoCaptor.getValue().campaignId()).isEqualTo(req);
        verify(promoService).markSent(req, 2, null);

        // B15b — une ligne in-app 'promotion' par user du segment, en plus du push.
        verify(notificationService, times(2)).create(inAppCaptor.capture());
        assertThat(inAppCaptor.getAllValues()).allSatisfy(n -> {
            assertThat(n.type()).isEqualTo("promotion");
            assertThat(n.channel()).isEqualTo("inapp");
            assertThat(n.title()).isEqualTo("T");
            assertThat(n.link()).isEqualTo("/l");
        });
        assertThat(inAppCaptor.getAllValues().stream().map(NotificationCreateDto::recipientUserId).toList())
            .isEqualTo(ids);
    }

    @Test
    void inAppFailureForOneUser_doesNotBlockPush() {
        UUID req = UUID.randomUUID();
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
        // 1re création in-app jette → ne doit pas empêcher le push ni le markSent.
        when(notificationService.create(any())).thenThrow(new RuntimeException("boom")).thenReturn(null);
        when(pushService.sendPromo(any())).thenReturn(new PushResultDto(2, 2, List.of(), "ok"));

        listener.onPromoAudienceResolved(new PromoAudienceResolvedEvent(req, ids, "T", "B", "/l", Instant.now()));

        verify(notificationService, times(2)).create(any()); // tentées pour les 2
        verify(pushService).sendPromo(any());                 // push quand même envoyé
        verify(promoService).markSent(req, 2, null);
    }

    @Test
    void partialFailure_recordsMessageAsError() {
        UUID req = UUID.randomUUID();
        when(pushService.sendPromo(any())).thenReturn(new PushResultDto(1, 2, List.of("x"), "sent 1/2"));

        listener.onPromoAudienceResolved(new PromoAudienceResolvedEvent(
            req, List.of(UUID.randomUUID(), UUID.randomUUID()), "T", "B", null, Instant.now()));

        verify(promoService).markSent(req, 1, "sent 1/2");
    }

    @Test
    void noRecipients_marksSentZero_andSkipsPush() {
        UUID req = UUID.randomUUID();
        listener.onPromoAudienceResolved(new PromoAudienceResolvedEvent(req, List.of(), "T", "B", null, Instant.now()));
        verify(promoService).markSent(req, 0, "no recipients");
        verify(pushService, never()).sendPromo(any());
    }

    /**
     * Résilience (fix promo-push) : si l'envoi FCM jette (ex: nettoyage d'un token périmé qui dérape
     * pendant le fan-out dans la transaction async), le listener NE doit PAS propager — sinon l'event
     * Modulith resterait incomplet (rejoué en boucle au restart) et la demande coincée en 'approved'.
     * On vérifie qu'il appelle quand même markSent (best-effort) et ne lève rien.
     */
    @Test
    void pushThrows_doesNotPropagate_stillMarksSent() {
        UUID req = UUID.randomUUID();
        when(notificationService.create(any())).thenReturn(null);
        when(pushService.sendPromo(any())).thenThrow(new RuntimeException("fcm boom"));

        // Ne doit pas jeter.
        listener.onPromoAudienceResolved(new PromoAudienceResolvedEvent(
            req, List.of(UUID.randomUUID()), "T", "B", "/l", Instant.now()));

        verify(notificationService).create(any());                         // in-app tentée
        verify(promoService).markSent(eq(req), eq(0), contains("push failed")); // markSent quand même
    }

    /**
     * Résilience (fix promo-push) : si markSent jette (incident DB sur la mise à jour de statut),
     * le listener NE doit PAS propager (l'essentiel — in-app + push — est déjà fait). Garantit que
     * l'event Modulith se complète et n'est pas rejoué indéfiniment.
     */
    @Test
    void markSentThrows_doesNotPropagate() {
        UUID req = UUID.randomUUID();
        when(pushService.sendPromo(any())).thenReturn(new PushResultDto(1, 1, List.of(), "ok"));
        doThrow(new RuntimeException("db boom")).when(promoService).markSent(any(), anyInt(), any());

        // Ne doit pas jeter malgré l'échec de markSent.
        listener.onPromoAudienceResolved(new PromoAudienceResolvedEvent(
            req, List.of(UUID.randomUUID()), "T", "B", null, Instant.now()));

        verify(pushService).sendPromo(any());
        verify(promoService).markSent(eq(req), eq(1), any());
    }
}
