package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Event publié par {@code FinancialCronJobs.alertExpiringContracts} pour chaque contrat partenaire
 * arrivant à un jalon d'expiration (J-30 / J-15 / J-7) et qui <b>ne sera pas auto-renouvelé</b>
 * ({@code auto_renew = false}) — donc qui va réellement expirer si personne n'agit.
 *
 * <p>Consommé par {@code NotificationEventHandler} → notification in-app (type {@code system},
 * avec {@code metadata = {kind:'contract_expiring', contractId, milestone}} pour l'anti-doublon du
 * cron) <b>et</b> push FCM, pour chaque admin destinataire. Parité de l'EF legacy
 * {@code notify-expiring-contracts} (qui n'était qu'in-app via {@code admin_notifications}).
 *
 * <p><b>Frontière Modulith</b> : les destinataires (admins plateforme = SUPERADMIN) sont résolus
 * <b>côté financial</b> (qui a la dépendance {@code core.identity}) via {@code UserDirectoryApi} et
 * portés sur l'event — exactement le pattern de {@code SeminarRequestedEvent} /
 * {@code AnnouncementPublishedEvent}. {@code core.notification} reste sans dépendance identity.
 */
public record ContractExpiringSoonEvent(
    UUID contractId,
    UUID restaurantId,
    String contractNumber,
    String restaurantName,
    String endsLabel,            // date de fin pré-formatée "JJ/MM/AAAA"
    String milestone,            // "j30" | "j15" | "j7"
    List<UUID> recipientAdminIds,
    Instant occurredAt
) {
}
