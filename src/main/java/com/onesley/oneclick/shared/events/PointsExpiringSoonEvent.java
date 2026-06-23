package com.onesley.oneclick.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Event publié par {@code LoyaltyCronJobs.alertExpiringPoints} pour chaque compte fidélité
 * dont des points {@code earn} arrivent à un jalon d'expiration (J-30 / J-15 / J-7).
 *
 * <p>Consommé par {@code NotificationEventHandler} → notification in-app (type {@code loyalty},
 * avec {@code metadata = {kind:'points_expiring', accountId, milestone}} pour l'anti-doublon du
 * cron) <b>et</b> push FCM. Parité de l'EF legacy {@code notify-expiring-points} (qui n'était que
 * in-app) ; ici on profite de la pile push réelle (B.8.4).
 *
 * <p><b>Frontière Modulith</b> : le module {@code loyalty} ne dépend pas de {@code core.notification} ;
 * il publie ce record dans le package OPEN {@code shared.events}. Le destinataire est résolu côté
 * loyalty ({@code loyalty_accounts.client_id}) — aucune dépendance cross-module ajoutée.
 */
public record PointsExpiringSoonEvent(
    UUID recipientUserId,   // loyalty_accounts.client_id
    UUID accountId,         // loyalty_accounts.id — porté dans metadata pour l'anti-doublon
    int points,             // somme des points earn expirant à ce jalon
    String expiresLabel,    // date d'expiration pré-formatée "JJ/MM"
    String milestone,       // "j30" | "j15" | "j7"
    Instant occurredAt
) {
}
