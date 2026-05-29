package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.AdminViewsDtos.GroupRestaurantRollupDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * B1.2 — Publisher temps réel du dashboard groupe (GroupDashboard / PulsePro).
 *
 * <p>Contrairement aux dashboards admin globaux ({@link AdminExecDashboardPublisher} et
 * consorts, qui poussent UN snapshot plateforme sur un topic partagé), ce dashboard est
 * <b>scopé par utilisateur</b> : chaque manager/groupe ne voit que SES restaurants. On
 * pousse donc sur la <b>queue privée</b> {@code /user/queue/group-dashboard} — Spring route
 * une destination {@code /user/**} uniquement vers le principal authentifié, ce qui garantit
 * l'isolation cross-tenant sans interceptor d'autorisation supplémentaire.
 *
 * <p>Le set d'ids restaurant est résolu <b>serveur-side</b> depuis les {@code restaurant_staffs}
 * du user (jamais transmis par le client) via
 * {@link AdminViewsService#groupDashboardRollupForUser(UUID)}.
 *
 * <p>Mécanique (mirror {@code AbstractDashboardPublisher}, adaptée au push par-user) :
 * recompute périodique borné, push uniquement si le snapshot du user a changé, gate sur
 * la présence d'au moins 1 abonné (0 charge DB à vide), push immédiat à l'abonnement.
 */
@Component
@Slf4j
public class GroupDashboardPublisher {

    /** Destination cliente d'abonnement (préfixe /user → queue privée du principal). */
    public static final String SUBSCRIBE_DESTINATION = "/user/queue/group-dashboard";
    /** Destination de publication (Spring ajoute le routage /user/{principal}). */
    static final String PUBLISH_DESTINATION = "/queue/group-dashboard";

    private final SimpMessagingTemplate messagingTemplate;
    private final AdminViewsService adminViewsService;

    /** sessionId → principalName (userId). Permet le nettoyage exact au disconnect (multi-session). */
    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    /** principalName → dernier snapshot poussé (détection de changement par-user). */
    private final Map<String, List<GroupRestaurantRollupDto>> lastByUser = new ConcurrentHashMap<>();

    public GroupDashboardPublisher(SimpMessagingTemplate messagingTemplate, AdminViewsService adminViewsService) {
        this.messagingTemplate = messagingTemplate;
        this.adminViewsService = adminViewsService;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        if (!SUBSCRIBE_DESTINATION.equals(accessor.getDestination())) {
            return;
        }
        String userId = principalName(event.getUser());
        if (userId == null) {
            return; // non authentifié (dev permissif) → rien à pousser de scopé
        }
        sessions.put(accessor.getSessionId(), userId);
        lastByUser.remove(userId); // force le push immédiat pour le nouvel abonné
        publishFor(userId);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String userId = sessions.remove(event.getSessionId());
        // Plus aucune session pour ce user → libère le cache de changement.
        if (userId != null && !sessions.containsValue(userId)) {
            lastByUser.remove(userId);
        }
    }

    @Scheduled(fixedDelayString = "${app.realtime.dashboard.interval-ms:30000}")
    public void scheduledPublish() {
        if (sessions.isEmpty()) {
            return; // personne connecté → 0 requête DB
        }
        // Set distinct de users abonnés (multi-session dédupliqué).
        sessions.values().stream().distinct().forEach(this::publishFor);
    }

    /** Calcule le rollup du user et pousse sur sa queue privée si changé. */
    private void publishFor(String userId) {
        try {
            List<GroupRestaurantRollupDto> snapshot =
                adminViewsService.groupDashboardRollupForUser(UUID.fromString(userId));
            if (snapshot.equals(lastByUser.get(userId))) {
                return; // aucun changement → pas de trafic inutile
            }
            lastByUser.put(userId, snapshot);
            messagingTemplate.convertAndSendToUser(userId, PUBLISH_DESTINATION, snapshot);
        } catch (RuntimeException e) {
            // Best-effort : un échec de push ne doit jamais casser le scheduler ni les autres users.
            log.warn("[ws] group-dashboard push échoué pour user {} : {}", userId, e.getMessage());
        }
    }

    private static String principalName(Principal p) {
        return p == null ? null : p.getName();
    }
}
