package com.onesley.oneclick.realtime;

import com.onesley.oneclick.shared.events.LoyaltyEarnedEvent;
import com.onesley.oneclick.shared.events.LoyaltyRedeemedEvent;
import com.onesley.oneclick.shared.events.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Push STOMP TEMPS RÉEL par-utilisateur (axe G — directive : temps réel = WebSocket, jamais polling).
 *
 * <p>Écoute des events métier (publiés APRÈS commit) et pousse un signal léger sur la <b>queue
 * privée</b> du user concerné :
 * <ul>
 *   <li>{@link LoyaltyEarnedEvent}/{@link LoyaltyRedeemedEvent} → {@code /user/queue/loyalty}
 *       (le front {@code useRealtimeLoyalty} invalide solde/punch/wallet/historique) ;</li>
 *   <li>{@link NotificationCreatedEvent} → {@code /user/queue/notifications}
 *       (le front {@code useNotificationsRealtime} rafraîchit la cloche + le badge).</li>
 * </ul>
 *
 * <p>Le payload est un simple <i>ping</i> : le REST reste la source de vérité, le front se contente
 * d'invalider la query react-query → re-fetch. Sécurité : Spring résout {@code /user/...} vers la
 * session du principal authentifié au CONNECT (= userId, sujet du JWT) — un user ne reçoit JAMAIS
 * la queue d'un autre. Frontière Modulith respectée : {@code realtime} (OPEN, infra partagée) écoute
 * des events de {@code shared} ; les modules métier ne dépendent pas de {@code realtime}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRealtimePublisher {

    /** Destination relative — le front s'abonne à {@code /user/queue/loyalty}. */
    static final String LOYALTY_DEST = "/queue/loyalty";
    /** Destination relative — le front s'abonne à {@code /user/queue/notifications}. */
    static final String NOTIFICATIONS_DEST = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    /** Signal léger poussé sur la queue privée (le front invalide + re-fetch). */
    public record RealtimePing(String type, Instant at) {}

    @ApplicationModuleListener
    public void onLoyaltyEarned(LoyaltyEarnedEvent ev) {
        pushLoyalty(ev.clientId(), "loyalty.earned");
    }

    @ApplicationModuleListener
    public void onLoyaltyRedeemed(LoyaltyRedeemedEvent ev) {
        pushLoyalty(ev.clientId(), "loyalty.redeemed");
    }

    @ApplicationModuleListener
    public void onNotificationCreated(NotificationCreatedEvent ev) {
        if (ev.recipientUserId() == null) return; // broadcast → pas de push ciblé
        push(ev.recipientUserId(), NOTIFICATIONS_DEST,
            new RealtimePing("notification.created", ev.occurredAt() != null ? ev.occurredAt() : Instant.now()));
    }

    private void pushLoyalty(UUID clientId, String type) {
        if (clientId == null) return;
        push(clientId, LOYALTY_DEST, new RealtimePing(type, Instant.now()));
    }

    private void push(UUID userId, String destination, RealtimePing ping) {
        try {
            // Best-effort : un échec de push ne doit jamais casser la transaction métier déjà commitée.
            messagingTemplate.convertAndSendToUser(userId.toString(), destination, ping);
        } catch (RuntimeException e) {
            log.warn("[ws] user-push échoué dest={} user={} : {}", destination, userId, e.getMessage());
        }
    }
}
