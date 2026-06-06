package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié quand le commercial (staff/admin) change le statut d'une demande de séminaire
 * ({@code PccSeminarService.updateStatus}, PCC).
 *
 * <p>Consommé par {@code NotificationEventHandler} (core/notification) qui crée la notification
 * in-app pour le MEMBRE organisateur ({@code organizerId}) — server-side (cohérent avec les
 * autres notifications relationnelles). Le libellé dépend du {@code newStatus} (templates portés
 * fidèlement de l'Edge Function legacy {@code send-pcc-seminar-status-update}), enrichi du
 * {@code companyName}.
 *
 * <p>Si {@code organizerId} est null (demande sans compte rattaché), le listener n'émet rien
 * (comme le legacy qui « skippait » la notif sans organizer_id).
 *
 * @param seminarId   id de la demande (deep-link côté membre)
 * @param organizerId destinataire de la notif = membre organisateur (peut être null)
 * @param changedBy   staff/admin auteur du changement de statut (traçabilité)
 * @param newStatus   nouveau statut (demandee|en_traitement|devis_envoye|confirmee|refusee|annulee)
 * @param companyName nom de l'entreprise (interpolé dans le titre de la notif)
 * @param occurredAt  horodatage du changement
 */
public record SeminarStatusChangedEvent(
    UUID seminarId,
    UUID organizerId,
    UUID changedBy,
    String newStatus,
    String companyName,
    Instant occurredAt
) {
}
