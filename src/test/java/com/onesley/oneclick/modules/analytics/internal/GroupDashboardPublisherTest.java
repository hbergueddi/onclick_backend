package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.AdminViewsDtos.GroupRestaurantRollupDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires isolés (Mockito, sans Spring ni DB) du {@link GroupDashboardPublisher} :
 * filtrage de destination, push immédiat à l'abonnement, gate « 0 abonné = 0 DB »,
 * détection de changement (pas de re-push si snapshot identique), nettoyage au disconnect.
 */
@ExtendWith(MockitoExtension.class)
class GroupDashboardPublisherTest {

    @Mock SimpMessagingTemplate messaging;
    @Mock AdminViewsService adminViews;
    @InjectMocks GroupDashboardPublisher publisher;

    private static final String USER = "11111111-1111-1111-1111-111111111111";

    private static GroupRestaurantRollupDto sample() {
        return new GroupRestaurantRollupDto(UUID.randomUUID(), new BigDecimal("100.00"), 50L,
            new BigDecimal("10.00"), 5L, 3L, 2L, 1L);
    }

    private SessionSubscribeEvent subscribe(String dest, String sessionId, String user) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acc.setDestination(dest);
        acc.setSessionId(sessionId);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
        Principal principal = user == null ? null : () -> user;
        return new SessionSubscribeEvent(this, msg, principal);
    }

    private SessionDisconnectEvent disconnect(String sessionId) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        acc.setSessionId(sessionId);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
        return new SessionDisconnectEvent(this, msg, sessionId, CloseStatus.NORMAL);
    }

    @Test
    void onSubscribe_matchingDest_pushesUserScopedSnapshot() {
        List<GroupRestaurantRollupDto> snap = List.of(sample());
        when(adminViews.groupDashboardRollupForUser(UUID.fromString(USER))).thenReturn(snap);

        publisher.onSubscribe(subscribe(GroupDashboardPublisher.SUBSCRIBE_DESTINATION, "s1", USER));

        verify(messaging).convertAndSendToUser(eq(USER), eq("/queue/group-dashboard"), eq(snap));
    }

    @Test
    void onSubscribe_otherDestination_ignored() {
        publisher.onSubscribe(subscribe("/topic/admin/exec", "s1", USER));
        verify(adminViews, never()).groupDashboardRollupForUser(any());
        verify(messaging, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void onSubscribe_noPrincipal_ignored() {
        publisher.onSubscribe(subscribe(GroupDashboardPublisher.SUBSCRIBE_DESTINATION, "s1", null));
        verify(adminViews, never()).groupDashboardRollupForUser(any());
    }

    @Test
    void scheduledPublish_noSubscribers_noDbNoPush() {
        publisher.scheduledPublish();
        verify(adminViews, never()).groupDashboardRollupForUser(any());
        verify(messaging, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void scheduledPublish_unchangedSnapshot_pushesOnceNotTwice() {
        List<GroupRestaurantRollupDto> snap = List.of(sample());
        when(adminViews.groupDashboardRollupForUser(UUID.fromString(USER))).thenReturn(snap);

        publisher.onSubscribe(subscribe(GroupDashboardPublisher.SUBSCRIBE_DESTINATION, "s1", USER)); // push #1
        publisher.scheduledPublish(); // même snapshot → pas de re-push

        verify(messaging, times(1)).convertAndSendToUser(eq(USER), eq("/queue/group-dashboard"), any());
    }

    @Test
    void onDisconnect_removesSession_scheduledThenNoPush() {
        when(adminViews.groupDashboardRollupForUser(UUID.fromString(USER))).thenReturn(List.of(sample()));
        publisher.onSubscribe(subscribe(GroupDashboardPublisher.SUBSCRIBE_DESTINATION, "s1", USER)); // 1 push
        publisher.onDisconnect(disconnect("s1"));

        publisher.scheduledPublish(); // plus d'abonné → gate → aucune nouvelle requête

        verify(adminViews, times(1)).groupDashboardRollupForUser(any()); // seulement celui de l'abonnement
    }
}
