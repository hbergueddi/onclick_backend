package com.onesley.oneclick.modules.analytics.internal;

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
 * Tests unitaires isolés (Mockito, sans Spring ni DB) du {@link RestaurantDashboardPublisher} (#10) :
 * le dashboard restaurateur est invalidé en temps réel via un {@link RealtimeSignal} PII-free poussé
 * sur {@code /topic/dashboard/{restaurantId}} à la création et au changement de statut d'une
 * réservation ; aucun push si {@code restaurantId} est null.
 */
@ExtendWith(MockitoExtension.class)
class RestaurantDashboardPublisherTest {

    @Mock SimpMessagingTemplate messaging;
    @InjectMocks RestaurantDashboardPublisher publisher;

    @Captor ArgumentCaptor<RealtimeSignal> signalCaptor;

    private static final UUID RESTO = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private static ReservationCreatedEvent created(UUID restaurantId) {
        return new ReservationCreatedEvent(
            UUID.randomUUID(), UUID.randomUUID(), restaurantId, UUID.randomUUID(),
            Instant.parse("2026-06-10T20:00:00Z"), 4, "requested", java.util.List.of());
    }

    private static ReservationStatusChangedEvent statusChanged(UUID restaurantId) {
        return new ReservationStatusChangedEvent(
            UUID.randomUUID(), UUID.randomUUID(), restaurantId, UUID.randomUUID(),
            "confirmed", "honored", null, Instant.parse("2026-06-10T22:00:00Z"));
    }

    @Test
    void topicFor_buildsPerRestaurantDashboardDestination() {
        assertThat(RestaurantDashboardPublisher.topicFor(RESTO))
            .isEqualTo("/topic/dashboard/" + RESTO);
    }

    @Test
    void onReservationCreated_pushesSignalOnDashboardTopic() {
        publisher.onReservationCreated(created(RESTO));

        verify(messaging).convertAndSend(eq(RestaurantDashboardPublisher.topicFor(RESTO)), signalCaptor.capture());
        RealtimeSignal signal = signalCaptor.getValue();
        assertThat(signal.scopeId()).isEqualTo(RESTO);
        assertThat(signal.reason()).isEqualTo("reservation.created");
    }

    @Test
    void onReservationStatusChanged_pushesSignalOnDashboardTopic() {
        publisher.onReservationStatusChanged(statusChanged(RESTO));

        verify(messaging).convertAndSend(eq(RestaurantDashboardPublisher.topicFor(RESTO)), signalCaptor.capture());
        assertThat(signalCaptor.getValue().reason()).isEqualTo("reservation.status_changed");
    }

    @Test
    void nullRestaurant_noPush() {
        publisher.onReservationCreated(created(null));
        publisher.onReservationStatusChanged(statusChanged(null));
        verify(messaging, never()).convertAndSend(any(String.class), any(Object.class));
    }
}
