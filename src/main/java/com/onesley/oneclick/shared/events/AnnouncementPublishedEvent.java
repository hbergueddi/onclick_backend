package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event publié quand une annonce tenant est <b>publiée</b> ({@code AnnouncementService.create} d'une
 * annonce déjà publiée, ou édition qui (re)publie — Lot 8).
 *
 * <p>Consommé par {@code NotificationEventHandler} (core/notification) qui crée la notification
 * in-app côté SERVEUR pour les destinataires — le <b>staff actif du tenant</b>, auteur exclu. Même
 * pattern que {@link FeedbackCreatedEvent} : on RÉSOUT les destinataires côté
 * {@code modules.announcement} (qui possède la read-view {@code findStaffRecipientIds} joignant
 * {@code restaurant_staffs}/{@code restaurants}) et on les porte sur l'event — le module
 * {@code core.notification} est CLOSED et n'a accès NI à {@code core.identity} NI à
 * {@code modules.restaurant} (frontière Modulith). Le listener n'a plus qu'à itérer.
 *
 * <h3>Pourquoi seulement à la publication</h3>
 * <p>Une annonce <b>programmée</b> (publish_at futur) NE déclenche PAS cet event à la création (le
 * staff ne doit pas être notifié avant la date) — le push différé est V1 hors scope (cron). Le
 * service ne publie l'event que si l'annonce est publiée à l'instant de la mutation.
 *
 * @param announcementId   id de l'annonce (deep-link {@code ?announcement=})
 * @param tenantId         tenant de l'annonce (scope des destinataires) — conservé pour traçabilité
 * @param authorId         tenant-admin auteur (exclu des {@code recipientUserIds})
 * @param recipientUserIds staff actif du tenant résolu côté announcement, dédoublonné, auteur exclu ;
 *                         peut être vide (aucun staff)
 * @param title            titre de l'annonce (libellé notif)
 * @param priority         {@code urgent} / {@code permanent} (libellé notif)
 * @param occurredAt       horodatage de publication
 */
public record AnnouncementPublishedEvent(
    UUID announcementId,
    UUID tenantId,
    UUID authorId,
    List<UUID> recipientUserIds,
    String title,
    String priority,
    Instant occurredAt
) {
}
