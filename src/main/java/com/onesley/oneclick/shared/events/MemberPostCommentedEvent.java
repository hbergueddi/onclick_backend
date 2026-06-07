package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event publié quand un membre commente un post du mur communautaire
 * ({@code MemberPostService.addComment}, Circle A.2).
 *
 * <p>Consommé par {@code NotificationEventHandler} (core/notification) qui crée les notifications
 * in-app côté SERVEUR (+ signal STOMP live via {@link NotificationCreatedEvent}) : l'auteur du post
 * (s'il n'est pas le commentateur) et les membres mentionnés. Server-side car le CLIENT qui commente
 * n'a pas {@code CREATE:NOTIFICATIONS} (un POST front 403'ait). Port du trigger DB legacy
 * {@code notify_post_commented} (member_circle phase 2).
 *
 * <h3>Pourquoi les destinataires sont portés sur l'event</h3>
 * <p>{@code core.notification} est CLOSED et ne dépend QUE de {@code audit, exception, security,
 * shared} — il n'a pas accès à {@code core.identity}. Les destinataires (auteur du post + mentionnés)
 * et le nom d'affichage du commentateur sont donc résolus côté {@code modules.membercircle} (qui a
 * {@code UserDirectoryApi} + l'entité post) puis portés sur l'event. Le listener n'a plus qu'à itérer.
 * Même invariant « 0 dépendance business↔business » que {@link FeedbackCreatedEvent}.
 *
 * @param postId             post commenté (deep-link {@code ?post=})
 * @param commenterId        auteur du commentaire (exclu des destinataires)
 * @param commenterName      « Prénom Nom » du commentateur (ou « Un membre »)
 * @param contentPreview     extrait du commentaire (≤ 100 car.) pour le corps de la notif
 * @param postAuthorRecipientId auteur du post à notifier, ou {@code null} si self-commentaire
 * @param mentionedRecipientIds membres mentionnés à notifier (déjà filtrés : ni commentateur ni auteur du post)
 * @param occurredAt         horodatage
 */
public record MemberPostCommentedEvent(
    UUID postId,
    UUID commenterId,
    String commenterName,
    String contentPreview,
    UUID postAuthorRecipientId,
    List<UUID> mentionedRecipientIds,
    Instant occurredAt
) {
}
