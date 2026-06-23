package com.onesley.oneclick.shared.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event publié quand une <b>restitution de points/CA</b> due à un restaurant est <b>versée</b>
 * (statut {@code paid} — Lot B6, {@code LoyaltyExtensionService.payRestitution}).
 *
 * <p>Consommé par {@code core.notification}
 * ({@code NotificationEventHandler.onRestaurantRestitutionPaid}) → notif in-app au <b>staff actif
 * du restaurant concerné</b> (« Restitution versée 💸 »). Même pattern que
 * {@link NoShowDisputeCreatedEvent} / {@link ResourceBookingCreatedEvent} : on RÉSOUT les
 * destinataires staff côté {@code modules.loyalty} (requête native sur {@code restaurant_staffs},
 * frontière Modulith) et on les porte sur l'event — le module {@code core.notification} (CLOSED)
 * n'a accès NI à {@code core.identity} NI à {@code modules.restaurant} et n'a qu'à itérer.
 *
 * <p>Le filtre de préférences staff (P1pref) appliqué côté listener relève de la catégorie
 * {@code loyalty} (une restitution est un versement de points/CA fidélité).
 *
 * @param restitutionId      id de la restitution versée
 * @param restaurantId       restaurant bénéficiaire (scope des destinataires) — traçabilité
 * @param amount             montant versé (MAD) — libellé notif (peut être {@code null})
 * @param staffRecipientIds  staff actif du resto résolu côté loyalty, dédoublonné ; peut être vide
 * @param occurredAt         horodatage du versement
 */
public record RestaurantRestitutionPaidEvent(
    UUID restitutionId,
    UUID restaurantId,
    BigDecimal amount,
    List<UUID> staffRecipientIds,
    Instant occurredAt
) {
}
