package com.onesley.oneclick.event;

import com.onesley.oneclick.entity.shared.ReservationStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Émis quand une réservation est créée (statut initial = {@code demandée}).
 *
 * <p>Listeners attendus en Phase 11+ :
 * <ul>
 *   <li>Push FCM au restaurateur ("Nouvelle demande de réservation")</li>
 *   <li>Email confirmation au client</li>
 *   <li>Insertion dans {@code admin_audit_log} (audit reservation create)</li>
 *   <li>Cron H-2 / J-1 : enregistrement pour rappels</li>
 * </ul>
 */
public record ReservationCreatedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID reservationId,
    UUID clientId,
    UUID restaurantId,
    LocalDate date,
    String heure,
    Integer couverts,
    ReservationStatus initialStatus
) implements DomainEvent {

    public static ReservationCreatedEvent of(
        UUID reservationId, UUID clientId, UUID restaurantId,
        LocalDate date, String heure, Integer couverts, ReservationStatus initialStatus
    ) {
        return new ReservationCreatedEvent(
            UUID.randomUUID(), Instant.now(),
            reservationId, clientId, restaurantId,
            date, heure, couverts, initialStatus
        );
    }

    @Override
    public String aggregateType() { return "reservation"; }

    @Override
    public UUID aggregateId() { return reservationId; }
}
