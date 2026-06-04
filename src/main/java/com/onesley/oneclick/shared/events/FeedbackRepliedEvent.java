package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand un owner (Adil) / admin répond à un avis membre
 * ({@code PccFeedbackService.reply}, PCC Lot 7).
 *
 * <p>Consommé par {@code NotificationEventHandler} (core/notification) qui crée la notification
 * in-app pour le MEMBRE original ({@code memberId}) — server-side (cohérent avec les autres
 * notifications relationnelles). Deep-link {@code /pocket/pcc/feedback?thread={feedbackId}} pour
 * ouvrir directement la réponse côté membre.
 *
 * <p>Port de l'Edge Function legacy {@code send-pcc-feedback-reply} (qui insérait la notification
 * pour {@code feedback.member_id} après l'UPDATE). {@code repliedBy} = l'owner/admin auteur de la
 * réponse (pour traçabilité).
 *
 * @param feedbackId id du feedback répondu (deep-link {@code ?thread=})
 * @param memberId   destinataire de la notif = auteur original de l'avis
 * @param repliedBy  owner/admin auteur de la réponse
 * @param sentiment  {@code happy} / {@code unhappy} (libellé notif)
 * @param occurredAt horodatage de la réponse
 */
public record FeedbackRepliedEvent(
    UUID feedbackId,
    UUID memberId,
    UUID repliedBy,
    String sentiment,
    Instant occurredAt
) {
}
