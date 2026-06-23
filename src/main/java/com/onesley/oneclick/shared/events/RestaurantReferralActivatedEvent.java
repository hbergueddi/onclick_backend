package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event publié quand un parrainage RESTAURANT-à-RESTAURANT (owner → owner) est ACTIVÉ
 * (consommation immédiate d'un code parrain par l'owner du resto filleul).
 *
 * <p>Consommé par DEUX listeners (frontière Modulith respectée — un seul event, plusieurs
 * consommateurs côté {@code core.notification} / {@code loyalty}) :
 * <ul>
 *   <li>module {@code loyalty} ({@code RestaurantReferralRewardListener}) : crédite
 *       {@code rewardPoints} de fidélité au <b>PARRAIN seul</b>, sur le compte
 *       ({@code referrerUserId}, {@code referrerRestaurantId}), avec la raison
 *       {@code RESTAURANT_REFERRAL}. Le filleul ne reçoit rien (spec métier validée) ;</li>
 *   <li>{@code core.notification} ({@code NotificationEventHandler#onRestaurantReferralActivated})
 *       (Lot B3) : notif in-app {@code system} à chaque <b>admin plateforme</b> (parité legacy
 *       {@code admin_notifications}).</li>
 * </ul>
 *
 * <p><b>Frontière Modulith</b> : seule communication sortante du module {@code restaurant_referral}
 * vers {@code loyalty} / {@code core.notification} — via ce record dans le package OPEN
 * {@code shared.events}. Aucun accès direct à l'entité/au service loyalty depuis le module referral
 * (calque {@link ResourceBookingStatusChangedEvent} → punch loyalty).
 *
 * <p>{@code referrerRestaurantId} est porté par l'event car c'est le resto sur lequel atterrit la
 * récompense (le parrain peut être owner de plusieurs restos ; on crédite celui qui a parrainé) —
 * ça évite au listener loyalty de re-résoudre le contexte cross-module.
 *
 * <p>{@code recipientAdminIds} (Lot B3) : destinataires de la notif admin (plateforme = SUPERADMIN),
 * résolus <b>côté {@code restaurant_referral}</b> (qui a la dépendance {@code core.identity}) via
 * {@code UserDirectoryApi.adminUserIds()} et portés sur l'event — exactement le pattern de
 * {@code ContractExpiringSoonEvent}. {@code core.notification} reste sans dépendance identity et n'a
 * qu'à itérer. Peut être vide (aucun admin résolu) → le listener ne notifie alors personne.
 */
public record RestaurantReferralActivatedEvent(
    UUID referralId,
    UUID referrerUserId,
    UUID referrerRestaurantId,
    int rewardPoints,
    UUID tenantId,
    List<UUID> recipientAdminIds,
    Instant occurredAt
) {
}
