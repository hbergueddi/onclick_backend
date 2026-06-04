package com.onesley.oneclick.shared.events;

import java.util.UUID;

/**
 * Event publié quand une contestation de no_show est résolue (Feature #3)
 * ({@code NoShowDisputeService.resolve}).
 *
 * <p>Consommé par le module loyalty ({@code ReservationRatingListener}) :
 * <ul>
 *   <li>{@code accepted = true}  → reverse la pénalité no_show
 *       ({@code recordRating(+penaliteNoShow, "dispute_accepted")}).</li>
 *   <li>{@code accepted = false} → aucune action (la pénalité initiale est conservée).</li>
 * </ul>
 *
 * <p>On reste dans la frontière Modulith : reservation publie l'event, loyalty le
 * consomme — aucun appel direct {@code reservation → loyalty}.
 */
public record NoShowDisputeResolvedEvent(
    UUID disputeId,
    UUID reservationId,
    UUID clientId,
    UUID restaurantId,
    boolean accepted
) {
}
