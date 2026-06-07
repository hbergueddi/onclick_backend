package com.onesley.oneclick.modules.system.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires isolés (Mockito, sans Spring ni DB) du {@link SystemHealthDashboardPublisher}
 * (audit R3) : topic correct, filtrage de destination, push immédiat à l'abonnement, gate
 * « 0 abonné = 0 requête DB », détection de changement (pas de re-push si snapshot identique).
 * L'{@code EntityManager} est mocké → l'empreinte {@code (count, max(checked_at))} est simulée.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SystemHealthDashboardPublisherTest {

    @Mock SimpMessagingTemplate messaging;
    @Mock EntityManager em;
    @Mock Query query;
    @InjectMocks SystemHealthDashboardPublisher publisher;

    // @InjectMocks fait l'injection par CONSTRUCTEUR (messaging) → ne field-injecte pas le
    // `@PersistenceContext em`. On le pose à la main (comme le ferait le conteneur JPA).
    @BeforeEach
    void injectEm() {
        org.springframework.test.util.ReflectionTestUtils.setField(publisher, "em", em);
    }

    /** Empreinte fixe : même valeur à chaque appel → permet de tester la détection de changement. */
    private void stubFingerprint() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(new Object[]{3L, Timestamp.from(Instant.parse("2026-06-07T10:00:00Z"))});
    }

    private SessionSubscribeEvent subscribe(String dest) {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        acc.setDestination(dest);
        acc.setSessionId("s1");
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
        return new SessionSubscribeEvent(this, msg);
    }

    private SessionConnectedEvent connected() {
        StompHeaderAccessor acc = StompHeaderAccessor.create(StompCommand.CONNECTED);
        Message<byte[]> msg = MessageBuilder.createMessage(new byte[0], acc.getMessageHeaders());
        return new SessionConnectedEvent(this, msg);
    }

    @Test
    void topic_isAdminSystemHealth() {
        assertThat(SystemHealthDashboardPublisher.TOPIC).isEqualTo("/topic/admin/system-health");
    }

    @Test
    void onSubscribe_matchingTopic_pushesSnapshot() {
        stubFingerprint();
        publisher.onSubscribe(subscribe(SystemHealthDashboardPublisher.TOPIC));
        verify(messaging).convertAndSend(eq(SystemHealthDashboardPublisher.TOPIC), any(Object.class));
    }

    @Test
    void onSubscribe_otherTopic_ignored_noDbNoPush() {
        publisher.onSubscribe(subscribe("/topic/admin/alerts"));
        verify(em, never()).createNativeQuery(anyString());
        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void scheduledPublish_noSubscribers_noDbNoPush() {
        publisher.scheduledPublish();
        verify(em, never()).createNativeQuery(anyString());
        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void scheduledPublish_unchangedSnapshot_pushesOnceNotTwice() {
        stubFingerprint();
        publisher.onConnected(connected());                                  // 1 session active
        publisher.onSubscribe(subscribe(SystemHealthDashboardPublisher.TOPIC)); // push #1
        publisher.scheduledPublish();                                        // même empreinte → pas de re-push
        verify(messaging, times(1)).convertAndSend(eq(SystemHealthDashboardPublisher.TOPIC), any(Object.class));
    }
}
