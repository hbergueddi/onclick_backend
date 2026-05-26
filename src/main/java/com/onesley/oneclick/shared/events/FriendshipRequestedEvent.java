package com.onesley.oneclick.shared.events;

import java.util.UUID;

/**
 * Event publié quand une demande d'amitié est créée ({@code SocialService.request}).
 *
 * <p>Consommé par {@code NotificationEventHandler} (core/notification) qui crée la
 * notification in-app « Demande d'ami » pour le destinataire — côté SERVEUR, car le
 * CLIENT n'a pas {@code CREATE:NOTIFICATIONS} (le POST /api/notifications front 403'ait).</p>
 */
public record FriendshipRequestedEvent(
    UUID friendshipId,
    UUID requesterId,
    UUID addresseeId
) {
}
