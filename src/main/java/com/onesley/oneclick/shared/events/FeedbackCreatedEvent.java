package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event publié quand un membre envoie un avis ({@code PccFeedbackService.create}, PCC Lot 7).
 *
 * <p>Consommé par {@code NotificationEventHandler} (core/notification) qui crée la notification
 * in-app côté SERVEUR pour les destinataires — l'owner du resto ciblé ({@code targetRestaurantId}
 * non null) et/ou les admins du tenant (avis général ou escalade). Server-side car le CLIENT qui
 * envoie n'a pas {@code CREATE:NOTIFICATIONS} (un POST front 403'ait). Même pattern que
 * {@link FriendshipRequestedEvent} / {@link FamilyMemberAddedEvent}.
 *
 * <p>Port de l'Edge Function legacy {@code send-pcc-feedback} (qui insérait directement les
 * notifications owners/tenant-admins après l'INSERT du feedback).
 *
 * <h3>Pourquoi les destinataires sont portés sur l'event ({@code recipientUserIds})</h3>
 * <p>Le module {@code core.notification} est CLOSED et ne dépend QUE de {@code audit, exception,
 * security, shared} — il n'a accès NI à {@code core.identity} (résolution des tenant-admins) NI à
 * {@code modules.restaurant} (owners d'un resto). Résoudre les destinataires dans le listener
 * violerait donc la frontière Modulith (et casserait {@code ModularityTests}). On résout les
 * destinataires côté {@code modules.feedback} (qui possède déjà les read-views natives owner-scope)
 * et on les porte sur l'event : le listener n'a plus qu'à itérer. Même invariant « 0 dépendance
 * business↔business » que pour {@link FamilyMemberAddedEvent} (qui porte directement
 * {@code relatedMemberId}).
 *
 * @param feedbackId         id du feedback créé (sert de deep-link {@code ?thread=})
 * @param memberId           auteur de l'avis (le caller) — exclu des {@code recipientUserIds}
 * @param tenantId           tenant du caller (scope des destinataires admin) — conservé pour traçabilité
 * @param targetRestaurantId resto PCC ciblé (owner notifié) ou {@code null} (avis général)
 * @param recipientUserIds   destinataires résolus côté feedback (owners du resto ciblé + tenant-admins),
 *                           dédoublonnés, auteur exclu ; peut être vide (aucun destinataire configuré)
 * @param sentiment          {@code happy} / {@code unhappy} (libellé notif)
 * @param category           catégorie textuelle de l'avis
 * @param occurredAt         horodatage de création
 */
public record FeedbackCreatedEvent(
    UUID feedbackId,
    UUID memberId,
    UUID tenantId,
    UUID targetRestaurantId,
    List<UUID> recipientUserIds,
    String sentiment,
    String category,
    Instant occurredAt
) {
}
