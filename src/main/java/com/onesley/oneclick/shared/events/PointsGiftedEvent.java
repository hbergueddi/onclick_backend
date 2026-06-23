package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié par {@code LoyaltyService.giftPoints()} quand un client offre des points à un autre
 * (don validé, bénéficiaire crédité dans la même transaction) — Lot B7 (décision user : OUI V1).
 *
 * <p>Consommé par {@code NotificationEventHandler.onPointsGifted} → notif in-app « Cadeau de points »
 * au BÉNÉFICIAIRE ({@code toUserId}). Server-side : le donneur n'a pas {@code CREATE:NOTIFICATIONS}.
 * In-app seul. {@code fromName} est best-effort (peut être {@code null} → le handler affiche « un ami »).
 */
public record PointsGiftedEvent(
    UUID fromUserId,
    UUID toUserId,
    int points,
    String fromName,
    Instant occurredAt
) {
}
