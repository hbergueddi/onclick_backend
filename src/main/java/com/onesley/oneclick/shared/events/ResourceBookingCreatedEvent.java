package com.onesley.oneclick.shared.events;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event publié quand un membre crée une réservation de RESSOURCE PCC (Padel/Spa/Golf/Coiffeur/
 * Palm Gym/…) — gap #3 : le staff du tenant doit être notifié d'une nouvelle demande à traiter
 * (parité legacy {@code send-pcc-staff-notification} sur nouveau {@code resource_booking}).
 *
 * <p>Consommé par {@code core.notification} ({@code NotificationEventHandler.onResourceBookingCreated})
 * → notif in-app + push à chaque staff. Les destinataires ({@code staffRecipientIds}) sont résolus
 * côté {@code resource_booking} (requête native sur {@code restaurant_staffs}/{@code restaurants} par
 * {@code tenant_id}) et portés sur l'event — frontière Modulith : {@code core.notification} n'a qu'à
 * itérer, comme {@code SeminarRequestedEvent}/{@code AnnouncementPublishedEvent}.
 */
public record ResourceBookingCreatedEvent(
    UUID bookingId,
    UUID organizerId,
    UUID resourceId,
    UUID tenantId,
    String resourceType,
    List<UUID> staffRecipientIds,
    Instant occurredAt
) {
}
