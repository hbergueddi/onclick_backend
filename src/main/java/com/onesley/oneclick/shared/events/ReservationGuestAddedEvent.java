package com.onesley.oneclick.shared.events;

import java.util.UUID;

/**
 * Event publié quand un guest IDENTIFIÉ (guestUserId connu) est ajouté à une réservation
 * ({@code ReservationGuestService.invite}).
 *
 * <p>Consommé par {@code NotificationEventHandler} (core/notification) qui crée la
 * notification in-app « Invitation à dîner » pour l'invité — côté SERVEUR (le CLIENT
 * organisateur n'a pas {@code CREATE:NOTIFICATIONS}). Non publié pour les guests
 * anonymes (téléphone/nom seul, sans compte).</p>
 */
public record ReservationGuestAddedEvent(
    UUID reservationId,
    UUID guestUserId,
    UUID invitedById
) {
}
