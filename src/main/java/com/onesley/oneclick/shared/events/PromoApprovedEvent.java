package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié par {@code PromoNotificationService.review()} quand une demande de push promo
 * passe au statut {@code approved}. Sprint R2 (parité push promo legacy {@code send-promo-push}).
 *
 * <p>Frontière Modulith : pur event-driven (aucune dépendance typée loyalty↔notification).
 * Le module {@code loyalty} l'écoute, résout l'audience du {@code segment} (ses propres tables
 * {@code loyalty_accounts}/{@code loyalty_transactions}/{@code tiers}) puis republie un
 * {@link PromoAudienceResolvedEvent} que {@code notification} consomme pour le fan-out FCM.
 */
public record PromoApprovedEvent(
    UUID requestId,
    UUID restaurantId,
    String segment,
    String title,
    String body,
    String link,
    Instant occurredAt
) {
}
