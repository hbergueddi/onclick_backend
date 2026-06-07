package com.onesley.oneclick.realtime;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.internal.NotificationService;
import com.onesley.oneclick.shared.events.LoyaltyEarnedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E STOMP par-utilisateur (axe G) — push TEMPS RÉEL loyalty + notifications sur les queues privées.
 *
 * <p>Exerce le chemin complet : handshake {@code /ws} → auth JWT au CONNECT (CLIENT réel) →
 * SUBSCRIBE {@code /user/queue/loyalty} | {@code /user/queue/notifications} → un event métier
 * commité ({@link LoyaltyEarnedEvent} / {@code NotificationService.create}) déclenche le push
 * scopé au principal. Vérifie aussi l'isolation : le payload arrive sur la queue du bon user.
 */
class UserRealtimeWebSocketIntegrationTests extends AbstractIntegrationTest {

    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired PlatformTransactionManager txManager;
    @Autowired NotificationService notificationService;

    private UUID anyClientId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL ORDER BY u.id LIMIT 1", String.class));
    }

    private StompSession connect(String bearer) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + bearer);
        return client.connectAsync("ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String dest) {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe(dest, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return Map.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        return received;
    }

    @Test
    void loyaltyEarned_pushesPingToClientLoyaltyQueue() throws Exception {
        UUID clientId = anyClientId();
        StompSession session = connect(jwtIssuer.issueAccessToken(clientId, "CLIENT").token());
        BlockingQueue<Map<String, Object>> received = subscribe(session, "/user/queue/loyalty");
        Thread.sleep(300); // laisse le SUBSCRIBE s'enregistrer côté broker

        // Event publié DANS une transaction commitée → @ApplicationModuleListener (AFTER_COMMIT) déclenché.
        new TransactionTemplate(txManager).executeWithoutResult(s ->
            eventPublisher.publishEvent(new LoyaltyEarnedEvent(
                UUID.randomUUID(), UUID.randomUUID(), clientId, UUID.randomUUID(), UUID.randomUUID(),
                100, new BigDecimal("100.0"), "snap2earn", Instant.now())));

        Map<String, Object> ping = received.poll(10, TimeUnit.SECONDS);
        assertThat(ping).as("le client doit recevoir un ping loyalty sur sa queue privée").isNotNull();
        assertThat(ping.get("type")).isEqualTo("loyalty.earned");
        session.disconnect();
    }

    @Test
    void notificationCreate_pushesPingToRecipientNotificationsQueue() throws Exception {
        UUID clientId = anyClientId();
        StompSession session = connect(jwtIssuer.issueAccessToken(clientId, "CLIENT").token());
        BlockingQueue<Map<String, Object>> received = subscribe(session, "/user/queue/notifications");
        Thread.sleep(300);

        // create() est @Transactional → publie NotificationCreatedEvent au commit → listener → push.
        notificationService.create(new NotificationCreateDto(
            clientId, "system", "inapp", "Bienvenue", "Test temps réel", null));

        Map<String, Object> ping = received.poll(10, TimeUnit.SECONDS);
        assertThat(ping).as("le destinataire doit recevoir un ping notification sur sa queue privée").isNotNull();
        assertThat(ping.get("type")).isEqualTo("notification.created");
        session.disconnect();
    }
}
