package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event publié par {@code PromotionCronJobs.expirePromotions()} quand une offre passe de
 * <b>active</b> à <b>expirée</b> (désactivée car {@code expires_at < NOW()}) — Lot B11.
 *
 * <p>Consommé par {@code NotificationEventHandler.onOfferExpired} → notif in-app au STAFF du
 * restaurant. Les destinataires ({@code staffRecipientIds}) sont résolus côté {@code promotion}
 * (requête native sur {@code restaurant_staffs} par {@code restaurant_id}) et portés sur l'event —
 * frontière Modulith : {@code core.notification} n'a qu'à itérer, comme
 * {@link ResourceBookingCreatedEvent}/{@link OfferCreatedEvent}.
 *
 * <p>Idempotence : le cron ne sélectionne que les offres au <b>passage actif→expiré</b> (toujours
 * {@code enabled = true} avant l'UPDATE), donc l'event n'est publié qu'une seule fois par offre.
 */
public record OfferExpiredEvent(
    UUID offerId,
    UUID restaurantId,
    List<UUID> staffRecipientIds,
    String title,
    Instant occurredAt
) {
}
