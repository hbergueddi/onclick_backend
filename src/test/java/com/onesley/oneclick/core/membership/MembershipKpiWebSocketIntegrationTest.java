package com.onesley.oneclick.core.membership;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.core.membership.api.MembershipKpiDto;
import com.onesley.oneclick.core.membership.internal.MembershipKpiPublisher;
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
 * Intégration P3 — dashboard <b>temps réel WebSocket/STOMP</b> des KPIs membres.
 *
 * <p>Exerce le chemin complet : handshake {@code /ws} → auth JWT au CONNECT → SUBSCRIBE sur
 * {@code /topic/admin/membership-kpis/{tenantId}} → push immédiat (onSubscribe → publishFor →
 * computeKpis). Confirme l'exigence « dashboards temps réel = WebSocket » (pas de polling).</p>
 */
class MembershipKpiWebSocketIntegrationTest extends AbstractIntegrationTest {

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
    void admin_subscribesMembershipKpis_receivesSnapshotOverWebSocket() throws Exception {
        UUID palmeraie = UUID.fromString(
            jdbc.queryForObject("SELECT id::text FROM tenants WHERE slug = 'palmeraie'", String.class));

        StompSession session = connect(adminBearer());
        BlockingQueue<MembershipKpiDto> received = new LinkedBlockingQueue<>();
        session.subscribe(MembershipKpiPublisher.topicFor(palmeraie), new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return MembershipKpiDto.class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                received.add((MembershipKpiDto) payload);
            }
        });

        MembershipKpiDto kpi = received.poll(10, TimeUnit.SECONDS);
        assertThat(kpi).as("snapshot KPIs membres poussé à l'abonnement (WebSocket)").isNotNull();
        assertThat(kpi.tenantId()).isEqualTo(palmeraie);
        assertThat(kpi.totalActive()).as("palmeraie a des membres actifs (backfill V90)").isGreaterThan(0);
        session.disconnect();
    }
}
