package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié par {@code ReservationCronJobs.finalizeNoShowPenalties()} quand la fenêtre de
 * contestation 48h d'un no_show expire sans contestation pending/accepted — Lot B10.
 *
 * <p>La pénalité de réputation a déjà été appliquée au <b>marquage</b> du no_show (cf.
 * {@code loyalty.ReservationRatingListener}, status→no_show). Cet event ne la ré-applique PAS :
 * il déclenche une simple notif in-app de <b>transparence</b> au client, indiquant que la pénalité
 * devient définitive (délai de contestation expiré). Consommé par
 * {@code NotificationEventHandler.onNoShowPenaltyFinalized} → notif in-app avec metadata anti-doublon.
 */
public record NoShowPenaltyFinalizedEvent(
    UUID clientId,
    UUID reservationId,
    Instant occurredAt
) {
}
