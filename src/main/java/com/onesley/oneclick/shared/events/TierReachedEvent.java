package com.onesley.oneclick.shared.events;


import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand un client FRANCHIT un palier de fidélité à la hausse (CH-3, candidat push
 * net-new). Émis par {@code LoyaltyService} dans le chemin de gain ({@code earnPoints}) UNIQUEMENT
 * lorsque le palier calculé sur l'agrégat de points du client (au sein du tenant) change — jamais
 * sur chaque gain (sinon spam).
 *
 * <p>Palier déterminé via la table {@code tiers} (par tenant, {@code min_points}) — conforme à la
 * règle « tier = seuils DB, jamais hardcodé ». Consommé par {@code NotificationEventHandler} →
 * notif in-app + push « Nouveau palier atteint 🎉 ».
 */
public record TierReachedEvent(
    UUID clientId,
    UUID tenantId,
    String tierName,
    int totalPoints,
    Instant occurredAt
) {
}
