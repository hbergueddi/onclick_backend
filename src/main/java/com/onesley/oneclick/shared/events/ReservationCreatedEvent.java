package com.onesley.oneclick.shared.events;

import org.springframework.modulith.events.Externalized;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand une réservation est créée — consommé par les modules en aval
 * (notification, loyalty, analytics).
 *
 * <p>Pattern Spring Modulith Phase 2 §21 spec senior :
 * <ul>
 *   <li>Publié via {@code ApplicationEventPublisher.publishEvent} dans
 *       {@code ReservationService.create}</li>
 *   <li>Consommé via {@code @ApplicationModuleListener} dans
 *       {@code NotificationEventHandler} (envoi push)</li>
 *   <li>Externalisé vers Kafka topic {@code reservation.created} via
 *       {@link Externalized} pour consommation cross-service (Phase 2.3+)</li>
 * </ul>
 *
 * <p>L'event {@link Externalized} signifie que Spring Modulith persiste l'event
 * dans {@code event_publication}, puis le republie vers Kafka. Si Kafka est down,
 * l'event reste en attente dans la DB (durability + retry automatique).
 *
 * <p>Le routing key est statique pour V1. En V2 on pourra extraire {@code restaurant_id}
 * pour le partitioning Kafka (sticky par resto).
 */
@Externalized("reservation.created")
public record ReservationCreatedEvent(
    UUID reservationId,
    UUID clientId,
    UUID restaurantId,
    UUID tenantId,
    Instant reservationAt,
    int guestCount,
    String status
) {
}
