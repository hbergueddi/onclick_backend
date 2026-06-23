package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event publié par {@code StoreOnboardingService.create} quand un gérant soumet une demande
 * d'inscription d'enseigne (formulaire public, port de l'EF legacy {@code send-onboarding-request}).
 *
 * <p>Consommé par {@code NotificationEventHandler} → notification in-app (type {@code system},
 * deep-link page admin {@code /forge/demandes-inscription}) pour chaque <b>admin plateforme</b>.
 * Parité legacy : la table {@code admin_notifications} recevait une ligne « nouvelle demande
 * d'enseigne » à chaque soumission. <b>In-app seul</b> (pas de push requis — comme
 * {@code RestaurantReferralActivatedEvent} pour les notifs admin).
 *
 * <p><b>Frontière Modulith</b> : les destinataires (admins plateforme = SUPERADMIN) sont résolus
 * <b>côté store</b> (qui a la dépendance {@code core.identity}) via {@code UserDirectoryApi} et
 * portés sur l'event — exactement le pattern de {@link ContractExpiringSoonEvent} /
 * {@link RestaurantReferralActivatedEvent}. {@code core.notification} reste sans dépendance identity
 * et n'a qu'à itérer.
 *
 * @param requestId         id de la demande d'onboarding créée
 * @param restaurantName    nom commercial du resto candidat (corps de la notif)
 * @param contactName       nom du gérant demandeur (corps de la notif), peut être null
 * @param recipientAdminIds ids des SUPERADMIN destinataires (résolus côté store)
 * @param occurredAt        horodatage de création
 */
public record StoreOnboardingRequestedEvent(
    UUID requestId,
    String restaurantName,
    String contactName,
    String ownerEmail,
    String ownerFirstName,
    String city,
    List<UUID> recipientAdminIds,
    Instant occurredAt
) {
}
