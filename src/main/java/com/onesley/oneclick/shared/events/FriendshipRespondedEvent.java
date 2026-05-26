package com.onesley.oneclick.shared.events;

import java.util.UUID;

/**
 * Event publié quand une demande d'amitié reçoit une réponse (accept/decline)
 * ({@code SocialService.accept} / {@code decline}).
 *
 * <p>Consommé par {@code NotificationEventHandler} (core/notification) qui notifie
 * l'AUTRE partie (le demandeur) « Demande acceptée / refusée » — server-side, car le
 * CLIENT qui répond n'a pas {@code CREATE:NOTIFICATIONS} (le POST /api/notifications front 403'ait).</p>
 */
public record FriendshipRespondedEvent(
    UUID friendshipId,
    UUID recipientUserId,
    boolean accepted
) {
}
