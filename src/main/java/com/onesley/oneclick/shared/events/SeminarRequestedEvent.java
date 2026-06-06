package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event publié quand un membre soumet une demande de séminaire B2B
 * ({@code PccSeminarService.create}, PCC).
 *
 * <p>Consommé par {@code NotificationEventHandler} (core/notification) qui crée la notification
 * in-app côté SERVEUR pour les destinataires — le staff/admin (« commercial ») du tenant.
 * Server-side car le CLIENT qui soumet n'a pas {@code CREATE:NOTIFICATIONS} (un POST front
 * 403'ait). Même pattern que {@link FeedbackCreatedEvent} / {@link AnnouncementPublishedEvent}.
 *
 * <p>Port de l'Edge Function legacy {@code send-pcc-seminar-status-update} (côté commercial) /
 * de la RPC {@code create_seminar_request} (qui « notifiait le commercial » à la création).
 *
 * <h3>Pourquoi les destinataires sont portés sur l'event ({@code recipientUserIds})</h3>
 * <p>Le module {@code core.notification} est CLOSED et n'a accès NI à {@code core.identity}
 * (résolution des staff/admins d'un tenant) NI aux modules business — résoudre les destinataires
 * dans le listener violerait la frontière Modulith. On résout les destinataires côté
 * {@code modules.seminar} (read-view native staff/admin du tenant) et on les porte sur l'event :
 * le listener n'a plus qu'à itérer. Même invariant que {@link FeedbackCreatedEvent}.
 *
 * @param seminarId        id de la demande créée (deep-link inbox commercial)
 * @param organizerId      membre auteur de la demande (le caller) — exclu des {@code recipientUserIds}
 * @param tenantId         tenant du caller (scope des destinataires) — conservé pour traçabilité
 * @param recipientUserIds staff/admins actifs du tenant, dédoublonnés, auteur exclu ; peut être vide
 * @param companyName      nom de l'entreprise (libellé notif)
 * @param occurredAt       horodatage de création
 */
public record SeminarRequestedEvent(
    UUID seminarId,
    UUID organizerId,
    UUID tenantId,
    List<UUID> recipientUserIds,
    String companyName,
    Instant occurredAt
) {
}
