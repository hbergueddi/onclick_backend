package com.onesley.oneclick.core.membership.internal;

import com.onesley.oneclick.core.membership.api.MembershipKpiDto;
import com.onesley.oneclick.shared.events.MembershipActivatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publication <b>temps réel (WebSocket/STOMP)</b> des KPIs membres d'un programme — dashboard
 * « Membres » de l'admin tenant (P3). Calqué sur {@code TenantKpisPublisher} : push à l'abonnement,
 * re-push planifié <b>uniquement si le snapshot change</b>, et push immédiat sur activation d'une
 * membership ({@link MembershipActivatedEvent}, après commit). Aucun polling côté front.
 *
 * <p>Topic : {@code /topic/admin/membership-kpis/{tenantId}}.</p>
 */
@Component
@Slf4j
public class MembershipKpiPublisher {

    /** Préfixe du topic ; chemin complet = préfixe + tenantId. */
    static final String PREFIX = "/topic/admin/membership-kpis/";

    private final SimpMessagingTemplate messagingTemplate;
    private final MembershipQueryService queryService;

    /** sessionId → destination abonnée (cleanup exact au disconnect, multi-session). */
    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    /** destination → dernier snapshot poussé (détection de changement par tenant). */
    private final Map<String, MembershipKpiDto> lastByDest = new ConcurrentHashMap<>();

    public MembershipKpiPublisher(SimpMessagingTemplate messagingTemplate, MembershipQueryService queryService) {
        this.messagingTemplate = messagingTemplate;
        this.queryService = queryService;
    }

    /** Topic complet pour un tenant (réutilisable côté front/tests). */
    public static String topicFor(UUID tenantId) {
        return PREFIX + tenantId;
    }

    /** Extrait le tenantId d'une destination {@code /topic/admin/membership-kpis/{uuid}} ; null si invalide. */
    static UUID parseTenantId(String destination) {
        if (destination == null || !destination.startsWith(PREFIX)) {
            return null;
        }
        String suffix = destination.substring(PREFIX.length());
        if (suffix.isBlank() || suffix.contains("/")) {
            return null;
        }
        try {
            return UUID.fromString(suffix);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String dest = accessor.getDestination();
        if (parseTenantId(dest) == null) {
            return; // pas un topic membership-kpis valide
        }
        sessions.put(accessor.getSessionId(), dest);
        lastByDest.remove(dest); // force le push immédiat pour le nouvel abonné
        publishFor(dest);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String dest = sessions.remove(event.getSessionId());
        if (dest != null && !sessions.containsValue(dest)) {
            lastByDest.remove(dest);
        }
    }

    @Scheduled(fixedDelayString = "${app.realtime.dashboard.interval-ms:30000}")
    public void scheduledPublish() {
        if (sessions.isEmpty()) {
            return; // personne abonné → 0 requête DB
        }
        sessions.values().stream().distinct().forEach(this::publishFor);
    }

    /** Push immédiat quand une membership devient active (invite/réactivation), si un admin écoute. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipActivated(MembershipActivatedEvent ev) {
        if (ev == null || ev.tenantId() == null) {
            return;
        }
        String dest = topicFor(ev.tenantId());
        if (sessions.containsValue(dest)) {
            publishFor(dest);
        }
    }

    /** Calcule les KPIs du tenant ciblé et pousse sur son topic si le snapshot a changé. */
    private void publishFor(String dest) {
        UUID tenantId = parseTenantId(dest);
        if (tenantId == null) {
            return;
        }
        try {
            MembershipKpiDto snapshot = queryService.computeKpis(tenantId);
            if (snapshot.equals(lastByDest.get(dest))) {
                return; // aucun changement → pas de trafic inutile
            }
            lastByDest.put(dest, snapshot);
            messagingTemplate.convertAndSend(dest, snapshot);
        } catch (RuntimeException e) {
            log.warn("[ws] membership-kpis push échoué ({}) : {}", dest, e.getMessage());
        }
    }
}
