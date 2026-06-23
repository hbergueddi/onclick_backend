package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event publié par {@code loyalty} après résolution de l'audience d'un segment promo (Sprint R2).
 * Consommé par {@code notification} → fan-out FCM via {@code FcmPushService.sendPromo} + {@code markSent}.
 *
 * <p>Découple totalement loyalty (qui sait QUI cibler) de notification (qui sait POUSSER),
 * sans dépendance typée entre les deux modules.
 */
public record PromoAudienceResolvedEvent(
    UUID requestId,
    List<UUID> userIds,
    String title,
    String body,
    String link,
    Instant occurredAt
) {
}
