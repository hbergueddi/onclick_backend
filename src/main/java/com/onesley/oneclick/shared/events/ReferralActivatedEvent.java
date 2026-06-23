package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand un parrainage CLIENT (code parrain consommé au signup / activation manuelle)
 * est ACTIVÉ — Lot B1. Distinct de {@link RestaurantReferralActivatedEvent} (parrainage
 * RESTAURANT-à-restaurant, owner→owner, qui notifie les admins plateforme).
 *
 * <p>Parité legacy : à l'activation d'un parrainage client, le PARRAIN <i>et</i> le FILLEUL
 * recevaient une notif in-app (+ 50 pts crédités côté legacy). Le crédit de points n'existe pas
 * dans ce port Spring (la RPC {@code activate_referral_by_code} ne créditait rien — cf.
 * {@code SocialService.activateByCode}) ; ce qui manquait, ce sont les <b>2 notifs in-app</b>.
 *
 * <p>Consommé par {@code core.notification}
 * ({@code NotificationEventHandler.onReferralActivated}) → notif in-app {@code loyalty}
 * (deep-link Vault, car c'est +points) aux deux users, avec des titres distincts (parrain vs
 * filleul). <b>In-app SEUL</b> (parité legacy — pas de push).
 *
 * <p><b>Frontière Modulith</b> : seule communication sortante du module {@code social} vers
 * {@code core.notification} pour ce flux — via ce record dans le package OPEN {@code shared.events}.
 * Aucun appel direct {@code SocialService → NotificationService} (calque
 * {@code FriendshipRequestedEvent}/{@code FriendshipRespondedEvent}).
 *
 * <p>{@code rewardPoints} est porté informativement (libellé « +{pts} pts »). Si le parrain ou le
 * filleul est {@code null}, le listener skip le destinataire concerné via {@code createInApp}.
 */
public record ReferralActivatedEvent(
    UUID referralId,
    /** Le parrain (propriétaire du code parrain consommé). */
    UUID referrerUserId,
    /** Le filleul (caller qui a saisi le code, ou user rattaché à l'activation manuelle). */
    UUID referredUserId,
    /** Points crédités à chaque partie côté legacy (informational pour le libellé notif). */
    int rewardPoints,
    Instant occurredAt
) {
}
