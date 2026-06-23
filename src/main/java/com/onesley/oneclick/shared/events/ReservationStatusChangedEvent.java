package com.onesley.oneclick.shared.events;


import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event publié quand le statut d'une réservation change (confirmed/refused/cancelled/...).
 *
 * <p>Consommé par notification-service → notif au client (et, Lot B9, au staff quand le CLIENT
 * annule). Et par loyalty-service (ré-crédit / pénalité réputation).
 *
 * <p><b>Lot B9</b> : pour router la notif d'annulation côté staff, l'event porte désormais
 * <ul>
 *   <li>{@code changedBy} — l'acteur du changement de statut (le client de la résa, le staff,
 *       ou {@code null} pour un changement système comme l'auto-expiration H-2). Permet au
 *       listener de ne notifier le staff QUE quand le CLIENT lui-même annule (pas quand le staff
 *       annule — anti self-notify) ;</li>
 *   <li>{@code staffRecipientIds} — staff actif du restaurant, résolu côté module {@code reservation}
 *       (frontière Modulith) et porté sur l'event (calque {@code ReservationCreatedEvent}). Peut être
 *       {@code null}/vide (ex: changement système où on ne notifie pas le staff).</li>
 * </ul>
 *
 * <p>Les deux champs ont une valeur par défaut {@code null} via le constructeur de compatibilité
 * (les call sites historiques — crons, tests — n'ont pas à les fournir).
 */
public record ReservationStatusChangedEvent(
    UUID reservationId,
    UUID clientId,
    UUID restaurantId,
    UUID tenantId,
    String oldStatus,
    String newStatus,
    String reason,
    /** Lot B9 — acteur du changement (client / staff / null=système). */
    UUID changedBy,
    /** Lot B9 — staff actif du resto à notifier sur annulation par le client (peut être null/vide). */
    List<UUID> staffRecipientIds,
    Instant occurredAt
) {
    /**
     * Constructeur de compatibilité (pré-B9) : pas d'info d'acteur ni de destinataires staff.
     * {@code changedBy}/{@code staffRecipientIds} défaultés à {@code null} → le listener ne notifie
     * alors PAS le staff (comportement historique : seul le client était notifié).
     */
    public ReservationStatusChangedEvent(
            UUID reservationId, UUID clientId, UUID restaurantId, UUID tenantId,
            String oldStatus, String newStatus, String reason, Instant occurredAt) {
        this(reservationId, clientId, restaurantId, tenantId, oldStatus, newStatus, reason,
            null, null, occurredAt);
    }
}
