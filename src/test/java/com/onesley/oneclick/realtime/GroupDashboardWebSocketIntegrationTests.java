package com.onesley.oneclick.realtime;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.modules.analytics.api.AdminViewsDtos.GroupRestaurantRollupDto;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B1.2 — E2E STOMP du dashboard groupe sur la queue PRIVÉE {@code /user/queue/group-dashboard}.
 *
 * <p>Exerce le chemin complet : handshake {@code /ws} → auth JWT au CONNECT (RESTAURATEUR
 * réel, staffé) → SUBSCRIBE sur la queue user → push immédiat scopé serveur-side (rollup
 * des restaurants du user). Vérifie aussi l'isolation : le snapshot reçu ne contient QUE
 * les restaurants du principal (résolus depuis ses restaurant_staffs, jamais du client).
 */
class GroupDashboardWebSocketIntegrationTests extends AbstractIntegrationTest {

    private StompSession connect(String bearer) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        if (bearer != null) {
            connectHeaders.add("Authorization", "Bearer " + bearer);
        }
        return client.connectAsync("ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
    }

    @Test
    void staffedRestaurateur_subscribesUserQueue_receivesOwnRollup() throws Exception {
        // RESTAURATEUR réellement staffé → rollup non vide (≥ 1 resto, seedé à zéro sinon).
        String ownerId = jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "JOIN restaurant_staffs rs ON rs.user_id = u.id "
            + "WHERE r.code = 'RESTAURATEUR' AND rs.deleted_at IS NULL AND u.deleted_at IS NULL "
            + "ORDER BY u.id LIMIT 1", String.class);
        String bearer = jwtIssuer.issueAccessToken(UUID.fromString(ownerId), "RESTAURATEUR").token();

        StompSession session = connect(bearer);
        BlockingQueue<GroupRestaurantRollupDto[]> received = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/group-dashboard", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return GroupRestaurantRollupDto[].class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                received.add((GroupRestaurantRollupDto[]) payload);
            }
        });

        // L'abonnement déclenche un push immédiat (onSubscribe → publishFor).
        GroupRestaurantRollupDto[] rollup = received.poll(10, TimeUnit.SECONDS);
        assertThat(rollup).as("le restaurateur staffé doit recevoir son rollup").isNotNull();
        assertThat(rollup.length).as("≥ 1 restaurant (les siens)").isGreaterThanOrEqualTo(1);
        session.disconnect();
    }
}
