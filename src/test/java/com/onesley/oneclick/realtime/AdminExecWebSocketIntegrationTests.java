package com.onesley.oneclick.realtime;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.modules.analytics.api.AdminStatsDto;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.TestPropertySource;
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
 * Bug 37 — Smoke E2E du pilote WebSocket : dashboard exécutif admin sur STOMP.
 *
 * <p>Boot avec oauth2 activé (hérité d'{@link AbstractIntegrationTest}) +
 * intervalle de push réduit pour un test rapide. Exerce le chemin complet :
 * handshake {@code /ws} → auth JWT au frame CONNECT → autorisation SUBSCRIBE
 * {@code /topic/admin/exec} → push {@link AdminStatsDto} → désérialisation client.
 */
@TestPropertySource(properties = "app.realtime.admin-exec.interval-ms=1000")
class AdminExecWebSocketIntegrationTests extends AbstractIntegrationTest {

    private WebSocketStompClient newClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client;
    }

    private StompSession connect(String bearer) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (bearer != null) {
            connectHeaders.add("Authorization", "Bearer " + bearer);
        }
        return newClient()
            .connectAsync("ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(), connectHeaders,
                new StompSessionHandlerAdapter() {})
            .get(5, TimeUnit.SECONDS);
    }

    @Test
    void superadmin_subscribesExecTopic_receivesStats() throws Exception {
        StompSession session = connect(adminBearer());
        BlockingQueue<AdminStatsDto> received = new LinkedBlockingQueue<>();

        session.subscribe("/topic/admin/exec", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return AdminStatsDto.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                received.add((AdminStatsDto) payload);
            }
        });

        // L'abonnement déclenche un push immédiat (onSubscribe → publishIfChanged).
        AdminStatsDto stats = received.poll(10, TimeUnit.SECONDS);
        assertThat(stats).as("le SUPERADMIN doit recevoir le snapshot exec").isNotNull();
        assertThat(stats.totalRestaurants()).isGreaterThanOrEqualTo(0);
        session.disconnect();
    }

    @Test
    void unknownUser_subscribeExecTopic_rejected_noData() throws Exception {
        // JWT signé valide mais sub = user inconnu → 0 autorité → SUBSCRIBE refusé.
        String bearer = jwtIssuer.issueAccessToken(UUID.randomUUID(), "CLIENT").token();
        StompSession session = connect(bearer);
        BlockingQueue<AdminStatsDto> received = new LinkedBlockingQueue<>();

        session.subscribe("/topic/admin/exec", new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return AdminStatsDto.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                received.add((AdminStatsDto) payload);
            }
        });

        // Abonnement rejeté par l'intercepteur → aucune donnée ne doit arriver.
        AdminStatsDto stats = received.poll(2, TimeUnit.SECONDS);
        assertThat(stats).as("un non-SUPERADMIN ne doit recevoir aucune donnée admin").isNull();
    }
}
