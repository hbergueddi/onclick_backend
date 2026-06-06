package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.AdminStatsDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.context.event.EventListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * C3b — Publisher temps réel du TenantDashboard (KPIs par tenant), SUPERADMIN-only.
 *
 * <p>Contrairement aux dashboards globaux (un snapshot sur un topic partagé), ce dashboard est
 * <b>scopé par tenant</b> via un topic paramétré {@code /topic/admin/tenant-kpis/{tenantId}}. À
 * l'abonnement on extrait le tenantId du chemin, on calcule l'agrégat de CE tenant
 * ({@code AdminStatsService.computeStats(tenantId)}) et on pousse ; un recompute périodique borné
 * réémet uniquement si le snapshot a changé. Pas de polling client.
 *
 * <p>Sécurité : le topic est sous le préfixe {@code /topic/admin/**} déjà gardé par
 * {@code StompAuthChannelInterceptor} (autorité SUPERADMIN requise à l'abonnement) — aucune nouvelle
 * règle de sécurité, on réutilise la garde existante (cohérent avec la décision « portail
 * tenant-admin SUPERADMIN-only »).
 *
 * <p>Mécanique calquée sur {@code GroupDashboardPublisher} (per-scope) : map sessionId→destination,
 * dédoublonnage multi-session, push immédiat à l'abonnement, gate « 0 abonné = 0 requête DB ».
 */
@Component
@Slf4j
public class TenantKpisPublisher {

    /** Préfixe du topic paramétré ; le chemin complet = préfixe + tenantId. */
    static final String PREFIX = "/topic/admin/tenant-kpis/";

    private final SimpMessagingTemplate messagingTemplate;
    private final AdminStatsService adminStatsService;

    /** sessionId → destination abonnée (cleanup exact au disconnect, multi-session). */
    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    /** destination → dernier snapshot poussé (détection de changement par tenant). */
    private final Map<String, AdminStatsDto> lastByDest = new ConcurrentHashMap<>();

    public TenantKpisPublisher(SimpMessagingTemplate messagingTemplate, AdminStatsService adminStatsService) {
        this.messagingTemplate = messagingTemplate;
        this.adminStatsService = adminStatsService;
    }

    /** Topic complet pour un tenant (réutilisable côté front/tests). */
    public static String topicFor(UUID tenantId) {
        return PREFIX + tenantId;
    }

    /** Extrait le tenantId d'une destination {@code /topic/admin/tenant-kpis/{uuid}} ; null si invalide. */
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
            return; // pas un topic tenant-kpis valide
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

    /** Calcule l'agrégat du tenant ciblé et pousse sur son topic si le snapshot a changé. */
    private void publishFor(String dest) {
        UUID tenantId = parseTenantId(dest);
        if (tenantId == null) {
            return;
        }
        try {
            AdminStatsDto snapshot = adminStatsService.computeStats(tenantId);
            if (snapshot.equals(lastByDest.get(dest))) {
                return; // aucun changement → pas de trafic inutile
            }
            lastByDest.put(dest, snapshot);
            messagingTemplate.convertAndSend(dest, snapshot);
        } catch (RuntimeException e) {
            log.warn("[ws] tenant-kpis push échoué ({}) : {}", dest, e.getMessage());
        }
    }
}
