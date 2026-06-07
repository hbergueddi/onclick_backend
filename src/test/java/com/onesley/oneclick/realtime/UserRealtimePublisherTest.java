package com.onesley.oneclick.realtime;

import com.onesley.oneclick.shared.events.LoyaltyEarnedEvent;
import com.onesley.oneclick.shared.events.LoyaltyRedeemedEvent;
import com.onesley.oneclick.shared.events.NotificationCreatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests unitaires Mockito de {@link UserRealtimePublisher} (axe G — push STOMP par-utilisateur).
 * Vérifie le ciblage queue privée par event + le no-op quand l'utilisateur cible est null.
 */
@ExtendWith(MockitoExtension.class)
class UserRealtimePublisherTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks UserRealtimePublisher publisher;

    @Test
    void loyaltyEarned_pushesToClientLoyaltyQueue() {
        UUID clientId = UUID.randomUUID();
        publisher.onLoyaltyEarned(new LoyaltyEarnedEvent(
            UUID.randomUUID(), UUID.randomUUID(), clientId, UUID.randomUUID(), UUID.randomUUID(),
            120, new BigDecimal("100.0"), "snap2earn", Instant.now()));
        verify(messagingTemplate).convertAndSendToUser(
            eq(clientId.toString()), eq("/queue/loyalty"), any(UserRealtimePublisher.RealtimePing.class));
    }

    @Test
    void loyaltyRedeemed_pushesToClientLoyaltyQueue() {
        UUID clientId = UUID.randomUUID();
        publisher.onLoyaltyRedeemed(new LoyaltyRedeemedEvent(
            UUID.randomUUID(), UUID.randomUUID(), clientId, UUID.randomUUID(), UUID.randomUUID(),
            50, new BigDecimal("50.0"), Instant.now()));
        verify(messagingTemplate).convertAndSendToUser(
            eq(clientId.toString()), eq("/queue/loyalty"), any(UserRealtimePublisher.RealtimePing.class));
    }

    @Test
    void notificationCreated_pushesToRecipientNotificationsQueue() {
        UUID recipient = UUID.randomUUID();
        publisher.onNotificationCreated(new NotificationCreatedEvent(
            recipient, UUID.randomUUID(), "loyalty", Instant.now()));
        verify(messagingTemplate).convertAndSendToUser(
            eq(recipient.toString()), eq("/queue/notifications"), any(UserRealtimePublisher.RealtimePing.class));
    }

    @Test
    void notificationCreated_nullRecipient_noPush() {
        publisher.onNotificationCreated(new NotificationCreatedEvent(
            null, UUID.randomUUID(), "system", Instant.now()));
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any(Object.class));
    }

    @Test
    void loyaltyEarned_nullClient_noPush() {
        publisher.onLoyaltyEarned(new LoyaltyEarnedEvent(
            UUID.randomUUID(), UUID.randomUUID(), null, UUID.randomUUID(), UUID.randomUUID(),
            10, BigDecimal.ONE, "snap2earn", Instant.now()));
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any(Object.class));
    }
}
