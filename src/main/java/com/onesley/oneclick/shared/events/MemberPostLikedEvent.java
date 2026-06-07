package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand un membre aime un post du mur communautaire — uniquement à la 1re pose du like
 * et jamais en self-like ({@code MemberPostService.toggleLike}, Circle A.2).
 *
 * <p>Consommé par {@code NotificationEventHandler} (core/notification) : notif in-app « ❤️ X a aimé
 * ton post » à l'auteur du post (+ signal STOMP live). Server-side (le CLIENT n'a pas
 * {@code CREATE:NOTIFICATIONS}). Port du trigger DB legacy {@code notify_post_liked}.
 *
 * <p>Le filtrage (1re pose + non self-like) est fait côté {@code modules.membercircle} : si l'event est
 * publié, l'auteur {@code postAuthorId} est garanti distinct du {@code likerId} → le listener notifie
 * sans condition. Destinataire + nom résolus côté émetteur (frontière Modulith), cf. {@link FeedbackCreatedEvent}.
 *
 * @param postId       post aimé (deep-link {@code ?post=})
 * @param likerId      auteur du like (traçabilité)
 * @param likerName    « Prénom Nom » du liker (ou « Un membre »)
 * @param postAuthorId auteur du post à notifier (garanti ≠ likerId)
 * @param occurredAt   horodatage
 */
public record MemberPostLikedEvent(
    UUID postId,
    UUID likerId,
    String likerName,
    UUID postAuthorId,
    Instant occurredAt
) {
}
