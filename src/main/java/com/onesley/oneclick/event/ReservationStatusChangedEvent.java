package com.onesley.oneclick.event;

import com.onesley.oneclick.entity.shared.ReservationStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Émis à chaque transition de statut d'une réservation.
 *
 * <p>Couvre les flows :
 * <ul>
 *   <li>{@code demandée → confirmée} (resto accepte)</li>
 *   <li>{@code demandée → refusée} (resto refuse)</li>
 *   <li>{@code demandée → contre_proposition} (resto propose autre créneau)</li>
 *   <li>{@code confirmée → honorée} (client présent)</li>
 *   <li>{@code confirmée → no_show} (client absent — workflow pénalité Session 37)</li>
 *   <li>{@code * → annulée} (annulation)</li>
 * </ul>
 *
 * <p>Listeners par scénario :
 * <ul>
 *   <li>{@code → confirmée} : push client + email confirmation</li>
 *   <li>{@code → honorée} : crédit FIFO points + ajustement reliability_score</li>
 *   <li>{@code → no_show} : pénalité reliability_score + workflow contestation 48h</li>
 *   <li>{@code → annulée} : libération créneau + remboursement points si applicable</li>
 *   <li>Tous : audit log + Sentry breadcrumb</li>
 * </ul>
 */
public record ReservationStatusChangedEvent(
    UUID eventId,
    Instant occurredAt,
    UUID reservationId,
    UUID clientId,
    UUID restaurantId,
    ReservationStatus previousStatus,
    ReservationStatus newStatus,
    UUID changedBy,
    String reason
) implements DomainEvent {

    public static ReservationStatusChangedEvent of(
        UUID reservationId, UUID clientId, UUID restaurantId,
        ReservationStatus previousStatus, ReservationStatus newStatus,
        UUID changedBy, String reason
    ) {
        return new ReservationStatusChangedEvent(
            UUID.randomUUID(), Instant.now(),
            reservationId, clientId, restaurantId,
            previousStatus, newStatus, changedBy, reason
        );
    }

    @Override
    public String aggregateType() { return "reservation"; }

    @Override
    public UUID aggregateId() { return reservationId; }
}
