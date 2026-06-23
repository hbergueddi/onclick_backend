package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.realtime.RealtimeSignal;
import com.onesley.oneclick.shared.events.ReservationCreatedEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests unitaires isolés (Mockito, sans Spring ni DB) du {@link ReservationDashboardPublisher} (#10) :
 * le publisher pousse un {@link RealtimeSignal} PII-free sur le topic <b>du restaurant concerné</b>
 * ({@code /topic/reservations/{restaurantId}}) à la création et au changement de statut, avec la
 * bonne {@code reason} ; aucun push si {@code restaurantId} est null.
 */
@ExtendWith(MockitoExtension.class)
class ReservationDashboardPublisherTest {

    @Mock SimpMessagingTemplate messaging;
    @InjectMocks ReservationDashboardPublisher publisher;

    @Captor ArgumentCaptor<RealtimeSignal> signalCaptor;

    private static final UUID RESTO = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");

    private static ReservationCreatedEvent created(UUID restaurantId) {
        return new ReservationCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), restaurantId, UUID.randomUUID(),
            Instant.parse("2026-06-10T19:00:00Z"), 2, "requested", java.util.List.of());
    }

    private static ReservationStatusChangedEvent statusChanged(UUID restaurantId) {
        return new ReservationStatusChangedEvent(
            UUID.randomUUID(), UUID.randomUUID(), restaurantId, UUID.randomUUID(),
            "requested", "confirmed", null, Instant.parse("2026-06-10T19:05:00Z"));
    }

    @Test
    void topicFor_buildsPerRestaurantDestination() {
        assertThat(ReservationDashboardPublisher.topicFor(RESTO))
            .isEqualTo("/topic/reservations/" + RESTO);
    }

    @Test
    void onReservationCreated_pushesCreatedSignalOnRestaurantTopic() {
        publisher.onReservationCreated(created(RESTO));

        verify(messaging).convertAndSend(eq(ReservationDashboardPublisher.topicFor(RESTO)), signalCaptor.capture());
        RealtimeSignal signal = signalCaptor.getValue();
        assertThat(signal.scopeId()).isEqualTo(RESTO);
        assertThat(signal.reason()).isEqualTo("reservation.created");
    }

    @Test
    void onReservationStatusChanged_pushesStatusSignalOnRestaurantTopic() {
        publisher.onReservationStatusChanged(statusChanged(RESTO));

        verify(messaging).convertAndSend(eq(ReservationDashboardPublisher.topicFor(RESTO)), signalCaptor.capture());
        assertThat(signalCaptor.getValue().reason()).isEqualTo("reservation.status_changed");
    }

    @Test
    void nullRestaurant_noPush() {
        publisher.onReservationCreated(created(null));
        publisher.onReservationStatusChanged(statusChanged(null));
        verify(messaging, never()).convertAndSend(any(String.class), any(Object.class));
    }
}
