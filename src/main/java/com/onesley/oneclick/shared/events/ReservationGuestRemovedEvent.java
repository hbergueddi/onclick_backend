package com.onesley.oneclick.shared.events;

import java.util.UUID;

/**
 * Event publié quand un organisateur RETIRE un invité IDENTIFIÉ (guestUserId connu) d'une
 * réservation ({@code ReservationGuestService.delete}) — Lot B14.
 *
 * <p>Consommé par {@code NotificationEventHandler} (core/notification) qui crée la
 * notification in-app « Invitation annulée » pour l'invité — côté SERVEUR (le CLIENT
 * organisateur n'a pas {@code CREATE:NOTIFICATIONS}). Non publié pour les guests
 * anonymes (téléphone/nom seul, sans compte). Calque {@link ReservationGuestAddedEvent}.</p>
 */
public record ReservationGuestRemovedEvent(
    UUID reservationId,
    UUID invitedUserId,
    String organizerName
) {
}
