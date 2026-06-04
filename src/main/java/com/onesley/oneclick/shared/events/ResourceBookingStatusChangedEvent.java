package com.onesley.oneclick.shared.events;


import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand le statut d'une réservation de RESSOURCE change
 * (pending/confirmed/cancelled/no_show/completed) — pendant PCC du
 * {@link ReservationStatusChangedEvent} (réservation restaurant).
 *
 * <p>Consommé par le module {@code loyalty} ({@code ResourceBookingPunchListener}) :
 * quand {@code newStatus == "completed"}, l'organisateur gagne +1 « punch » sur la
 * carte de fidélité de l'activité correspondante (mapping {@code resourceType → activity}).
 *
 * <p><b>Frontière Modulith</b> : seule communication sortante du module
 * {@code resource_booking} vers {@code loyalty} — via ce record dans le package OPEN
 * {@code shared.events}. Aucun appel direct {@code ResourceBookingService → PunchCardService}.
 *
 * <p>{@code tenantId} et {@code resourceType} sont portés par l'event car le module
 * {@code resource_booking} les connaît (via la ressource du booking), ce qui évite au
 * listener loyalty de résoudre la ressource cross-module (pas de dépendance ajoutée).
 */
public record ResourceBookingStatusChangedEvent(
    UUID bookingId,
    UUID organizerId,
    UUID resourceId,
    UUID tenantId,
    String resourceType,
    String oldStatus,
    String newStatus,
    Instant occurredAt
) {
}
