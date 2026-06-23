package com.onesley.oneclick.shared.events;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event publié quand le statut d'une réservation de RESSOURCE change
 * (pending/confirmed/cancelled/no_show/completed) — pendant PCC du
 * {@link ReservationStatusChangedEvent} (réservation restaurant).
 *
 * <p>Consommé par le module {@code loyalty} ({@code ResourceBookingPunchListener}) :
 * quand {@code newStatus == "completed"}, l'organisateur gagne +1 « punch » sur la
 * carte de fidélité de l'activité correspondante (mapping {@code resourceType → activity}).
 *
 * <p><b>Frontière Modulith</b> : seule communication sortante du module
 * {@code resource_booking} vers {@code loyalty} — via ce record dans le package OPEN
 * {@code shared.events}. Aucun appel direct {@code ResourceBookingService → PunchCardService}.
 *
 * <p>{@code tenantId} et {@code resourceType} sont portés par l'event car le module
 * {@code resource_booking} les connaît (via la ressource du booking), ce qui évite au
 * listener loyalty de résoudre la ressource cross-module (pas de dépendance ajoutée).
 */
public record ResourceBookingStatusChangedEvent(
    UUID bookingId,
    UUID organizerId,
    UUID resourceId,
    UUID tenantId,
    String resourceType,
    String oldStatus,
    String newStatus,
    /** Acteur du changement de statut (staff/admin OU le membre lui-même) — permet à
     *  notification de router l'annulation : push client uniquement si {@code changedBy != organizerId}. */
    UUID changedBy,
    /**
     * Lot B9 — staff actif du tenant à notifier quand le MEMBRE annule lui-même son booking
     * ({@code newStatus="cancelled"} et {@code changedBy == organizerId}). Résolu côté
     * {@code resource_booking} (frontière Modulith) et porté sur l'event ; {@code null}/vide
     * dans tous les autres cas (changement par staff/système, ou statut non-annulation) → pas
     * de notif staff. Calque {@code ResourceBookingCreatedEvent.staffRecipientIds}.
     */
    List<UUID> staffRecipientIds,
    Instant occurredAt
) {
    /**
     * Constructeur de compatibilité (pré-B9 staff branch) : pas de destinataires staff.
     * {@code staffRecipientIds} défaulté à {@code null} → le listener ne notifie pas le staff
     * (comportement historique). Utilisé par les call sites qui ne notifient que le client/loyalty
     * (cron auto-cancel, publication staff/admin où le staff n'a pas à s'auto-notifier).
     */
    public ResourceBookingStatusChangedEvent(
            UUID bookingId, UUID organizerId, UUID resourceId, UUID tenantId, String resourceType,
            String oldStatus, String newStatus, UUID changedBy, Instant occurredAt) {
        this(bookingId, organizerId, resourceId, tenantId, resourceType,
            oldStatus, newStatus, changedBy, null, occurredAt);
    }
}
