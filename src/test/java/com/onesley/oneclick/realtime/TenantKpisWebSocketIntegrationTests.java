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
 * C3b — Smoke E2E WebSocket du TenantDashboard temps réel (topic paramétré par tenant).
 *
 * <p>Chemin complet : handshake {@code /ws} → CONNECT JWT → SUBSCRIBE
 * {@code /topic/admin/tenant-kpis/{tenantId}} (gardé SUPERADMIN par StompAuthChannelInterceptor) →
 * push {@link AdminStatsDto} du tenant → désérialisation client. Un non-SUPERADMIN est rejeté.</p>
 */
@TestPropertySource(properties = "app.realtime.dashboard.interval-ms=1000")
class TenantKpisWebSocketIntegrationTests extends AbstractIntegrationTest {

    private UUID palmeraieTenantId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT id::text FROM tenants WHERE slug = 'palmeraie'", String.class));
    }

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
    void superadmin_subscribesTenantTopic_receivesStats() throws Exception {
        StompSession session = connect(adminBearer());
        BlockingQueue<AdminStatsDto> received = new LinkedBlockingQueue<>();
        String topic = "/topic/admin/tenant-kpis/" + palmeraieTenantId();

        session.subscribe(topic, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return AdminStatsDto.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                received.add((AdminStatsDto) payload);
            }
        });

        AdminStatsDto stats = received.poll(10, TimeUnit.SECONDS);
        assertThat(stats).as("le SUPERADMIN doit recevoir le snapshot KPIs du tenant").isNotNull();
        assertThat(stats.totalRestaurants()).isGreaterThanOrEqualTo(0);
        if (session.isConnected()) session.disconnect();
    }

    @Test
    void nonSuperAdmin_subscribeTenantTopic_rejected_noData() throws Exception {
        String bearer = jwtIssuer.issueAccessToken(UUID.randomUUID(), "CLIENT").token();
        StompSession session = connect(bearer);
        BlockingQueue<AdminStatsDto> received = new LinkedBlockingQueue<>();
        String topic = "/topic/admin/tenant-kpis/" + palmeraieTenantId();

        session.subscribe(topic, new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return AdminStatsDto.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                received.add((AdminStatsDto) payload);
            }
        });

        AdminStatsDto stats = received.poll(2, TimeUnit.SECONDS);
        assertThat(stats).as("un non-SUPERADMIN ne doit recevoir aucune donnée tenant").isNull();
        if (session.isConnected()) session.disconnect();
    }
}
