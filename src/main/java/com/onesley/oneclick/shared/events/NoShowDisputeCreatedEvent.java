package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event publié quand un CLIENT crée une contestation de no_show (Lot B8 —
 * {@code NoShowDisputeService.create}). Pendant « création » de
 * {@link NoShowDisputeResolvedEvent} (résolution).
 *
 * <p>Parité legacy : à la création d'une contestation, le staff du restaurant concerné était
 * notifié qu'une contestation à traiter venait d'arriver. Manquant côté Spring.
 *
 * <p>Consommé par {@code core.notification}
 * ({@code NotificationEventHandler.onDisputeCreated}) → notif in-app + push à chaque staff. Les
 * destinataires ({@code staffRecipientIds}) sont résolus côté {@code reservation} (requête native
 * sur {@code restaurant_staffs}, client contestataire exclu) et portés sur l'event — frontière
 * Modulith : {@code core.notification} n'a qu'à itérer (calque {@code ReservationCreatedEvent}).
 *
 * <p>{@code clientName} (optionnel) enrichit le corps de la notif ; peut être {@code null}.
 */
public record NoShowDisputeCreatedEvent(
    UUID disputeId,
    UUID reservationId,
    UUID restaurantId,
    List<UUID> staffRecipientIds,
    String clientName,
    Instant occurredAt
) {
}
