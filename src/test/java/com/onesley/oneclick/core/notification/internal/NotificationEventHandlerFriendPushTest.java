package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.shared.events.FriendshipRequestedEvent;
import com.onesley.oneclick.shared.events.FriendshipRespondedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * Tests unitaires R4 — les demandes d'ami créent la notif in-app ET poussent un FCM
 * (avant : in-app seulement). Parité legacy {@code send-friend-request-push}.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class NotificationEventHandlerFriendPushTest {

    @Mock NotificationService notificationService;
    @Mock FcmPushService pushService;
    @InjectMocks NotificationEventHandler handler;
    @Captor ArgumentCaptor<PushPromoDto> pushCaptor;

    @Test
    void onFriendshipRequested_createsInApp_andPushesToAddressee() {
        UUID addressee = UUID.randomUUID();
        UUID friendshipId = UUID.randomUUID();
        handler.onFriendshipRequested(new FriendshipRequestedEvent(friendshipId, UUID.randomUUID(), addressee));

        verify(notificationService).create(any(NotificationCreateDto.class));
        verify(pushService).sendPromo(pushCaptor.capture());
        assertThat(pushCaptor.getValue().userIds()).containsExactly(addressee);
        assertThat(pushCaptor.getValue().title()).contains("Demande d'ami");
        assertThat(pushCaptor.getValue().link()).isEqualTo(friendshipId.toString());
    }

    @Test
    void onFriendshipResponded_accepted_pushesToRecipient() {
        UUID recipient = UUID.randomUUID();
        handler.onFriendshipResponded(new FriendshipRespondedEvent(UUID.randomUUID(), recipient, true));

        verify(notificationService).create(any(NotificationCreateDto.class));
        verify(pushService).sendPromo(pushCaptor.capture());
        assertThat(pushCaptor.getValue().userIds()).containsExactly(recipient);
        assertThat(pushCaptor.getValue().title()).contains("acceptée");
    }
}
