package com.onesley.oneclick.realtime;

import com.onesley.oneclick.AbstractIntegrationTest;
import com.onesley.oneclick.shared.events.ReservationCreatedEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
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
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E STOMP par-restaurant (#10) — push TEMPS RÉEL des réservations + du dashboard restaurateur.
 *
 * <p>Exerce le chemin complet : handshake {@code /ws} → auth JWT au CONNECT → SUBSCRIBE
 * {@code /topic/reservations/{restaurantId}} ET {@code /topic/dashboard/{restaurantId}} → un event
 * réservation commité ({@link ReservationCreatedEvent} / {@link ReservationStatusChangedEvent})
 * déclenche les deux publishers ({@code ReservationDashboardPublisher} +
 * {@code RestaurantDashboardPublisher}) en {@code AFTER_COMMIT} → un {@link RealtimeSignal} PII-free
 * arrive sur les deux topics du restaurant ciblé.
 *
 * <p>Prouve la directive senior « temps réel = WebSocket, jamais polling » de bout en bout : event
 * métier → listener after-commit → broker STOMP → abonné. Reproductible, aucun service externe
 * (Kafka désactivé via {@code AbstractIntegrationTest}), {@code restaurantId} aléatoire (le topic
 * n'est qu'un canal d'invalidation, aucune lecture DB dans le publisher).
 */
class ReservationRealtimeWebSocketIntegrationTests extends AbstractIntegrationTest {

    @Autowired ApplicationEventPublisher eventPublisher;
    @Autowired PlatformTransactionManager txManager;

    /**
     * IDs réels seedés — on publie un VRAI {@link ReservationCreatedEvent} qui déclenche tout le
     * fan-out (notifications, loyalty…) ; des IDs aléatoires casseraient les FK des autres listeners.
     * Le restaurantId réel est aussi le scope du topic ({@code /topic/reservations/{id}}).
     */
    private UUID anyClientId() {
        return UUID.fromString(jdbc.queryForObject(
            "SELECT u.id::text FROM users u JOIN roles r ON r.id = u.role_id "
            + "WHERE r.code = 'CLIENT' AND u.deleted_at IS NULL ORDER BY u.id LIMIT 1", String.class));
    }

    /** {@code [restaurantId, tenantId]} d'un restaurant réel seedé. */
    private UUID[] anyRestaurant() {
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT id::text AS id, tenant_id::text AS tenant_id FROM restaurants "
            + "WHERE deleted_at IS NULL ORDER BY id LIMIT 1");
        return new UUID[]{
            UUID.fromString((String) row.get("id")),
            UUID.fromString((String) row.get("tenant_id"))
        };
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
    void reservationCreated_pushesSignalToReservationsAndDashboardTopics() throws Exception {
        UUID clientId = anyClientId();
        UUID[] resto = anyRestaurant();
        UUID restaurantId = resto[0];
        UUID tenantId = resto[1];
        StompSession session = connect(adminBearer());
        BlockingQueue<Map<String, Object>> reservations =
            subscribe(session, "/topic/reservations/" + restaurantId);
        BlockingQueue<Map<String, Object>> dashboard =
            subscribe(session, "/topic/dashboard/" + restaurantId);
        Thread.sleep(300); // laisse les SUBSCRIBE s'enregistrer côté broker

        // Event publié DANS une transaction commitée → @TransactionalEventListener(AFTER_COMMIT) déclenché.
        new TransactionTemplate(txManager).executeWithoutResult(s ->
            eventPublisher.publishEvent(new ReservationCreatedEvent(
                UUID.randomUUID(), clientId, restaurantId, tenantId,
                Instant.now(), 2, "requested", java.util.List.of())));

        Map<String, Object> resaPing = reservations.poll(10, TimeUnit.SECONDS);
        assertThat(resaPing).as("le topic réservations du restaurant doit recevoir un signal").isNotNull();
        assertThat(resaPing.get("scopeId")).isEqualTo(restaurantId.toString());
        assertThat(resaPing.get("reason")).isEqualTo("reservation.created");

        Map<String, Object> dashPing = dashboard.poll(10, TimeUnit.SECONDS);
        assertThat(dashPing).as("le topic dashboard du restaurant doit recevoir un signal").isNotNull();
        assertThat(dashPing.get("scopeId")).isEqualTo(restaurantId.toString());
        assertThat(dashPing.get("reason")).isEqualTo("reservation.created");

        session.disconnect();
    }

    @Test
    void reservationStatusChanged_pushesSignalToBothTopics() throws Exception {
        UUID clientId = anyClientId();
        UUID[] resto = anyRestaurant();
        UUID restaurantId = resto[0];
        UUID tenantId = resto[1];
        StompSession session = connect(adminBearer());
        BlockingQueue<Map<String, Object>> reservations =
            subscribe(session, "/topic/reservations/" + restaurantId);
        BlockingQueue<Map<String, Object>> dashboard =
            subscribe(session, "/topic/dashboard/" + restaurantId);
        Thread.sleep(300);

        new TransactionTemplate(txManager).executeWithoutResult(s ->
            eventPublisher.publishEvent(new ReservationStatusChangedEvent(
                UUID.randomUUID(), clientId, restaurantId, tenantId,
                "requested", "confirmed", null, Instant.now())));

        Map<String, Object> resaPing = reservations.poll(10, TimeUnit.SECONDS);
        assertThat(resaPing).as("le topic réservations doit recevoir le changement de statut").isNotNull();
        assertThat(resaPing.get("reason")).isEqualTo("reservation.status_changed");

        Map<String, Object> dashPing = dashboard.poll(10, TimeUnit.SECONDS);
        assertThat(dashPing).as("le topic dashboard doit recevoir le changement de statut").isNotNull();
        assertThat(dashPing.get("reason")).isEqualTo("reservation.status_changed");

        session.disconnect();
    }
}
