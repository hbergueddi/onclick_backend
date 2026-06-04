package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand un membre (A) ajoute un proche (B) à sa liste « Ma Famille »
 * ({@code PccFamilyService.addFamilyMember}, PCC Lot 5).
 *
 * <p>Consommé par {@code NotificationEventHandler} (core/notification) qui crée la
 * notification in-app « Ajouté à une famille » pour le proche ajouté ({@code relatedMemberId}) —
 * côté SERVEUR, car le CLIENT qui ajoute n'a pas {@code CREATE:NOTIFICATIONS} (un POST front
 * 403'ait). Même pattern que {@link FriendshipRequestedEvent}.
 *
 * <p>Publié uniquement lors d'une création RÉELLE (pas sur un ajout idempotent « déjà ajouté »),
 * pour ne pas re-notifier le proche à chaque tentative ré-émise par le front.</p>
 */
public record FamilyMemberAddedEvent(
    UUID memberId,
    UUID relatedMemberId,
    Instant occurredAt
) {
}
