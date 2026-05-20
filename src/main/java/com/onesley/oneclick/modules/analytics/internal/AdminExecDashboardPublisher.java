package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.modules.analytics.api.AdminStatsFullDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bug 37 — Publisher temps réel du dashboard exécutif admin (pilote WebSocket).
 *
 * <p>Pattern « agrégat » : on ne pousse PAS sur chaque écriture (qui ferait
 * re-calculer 17 COUNT à chaque ligne insérée → explosion DB). À la place :
 * <ul>
 *   <li>recompute périodique ({@code @Scheduled}) borné dans le temps,</li>
 *   <li>push UNIQUEMENT si le snapshot a changé (compare {@code equals}),</li>
 *   <li>gate sur la présence d'au moins 1 session WS (sinon 0 charge DB quand
 *       personne ne regarde),</li>
 *   <li>push immédiat à l'abonnement (UX : pas d'attente de l'intervalle).</li>
 * </ul>
 *
 * <p>Réutilise {@link AdminStatsService} (même module analytics — pas de
 * violation Modulith). Le seul lien « réseau » est {@link SimpMessagingTemplate}
 * (bean framework). Topic : {@value #TOPIC} (autorisé SUPERADMIN par
 * {@code StompAuthChannelInterceptor}).
 */
@Component
@Slf4j
public class AdminExecDashboardPublisher {

    public static final String TOPIC = "/topic/admin/exec";

    private final SimpMessagingTemplate messagingTemplate;
    private final AdminStatsFullService statsService;

    /** Nombre de sessions WS actives — gate pour ne pas charger la DB à vide. */
    private final AtomicInteger activeSessions = new AtomicInteger(0);
    /** Dernier snapshot poussé — pour ne push que sur changement. */
    private volatile AdminStatsFullDto lastPushed;

    public AdminExecDashboardPublisher(SimpMessagingTemplate messagingTemplate, AdminStatsFullService statsService) {
        this.messagingTemplate = messagingTemplate;
        this.statsService = statsService;
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        activeSessions.incrementAndGet();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        activeSessions.updateAndGet(n -> Math.max(0, n - 1));
    }

    /** Push immédiat dès qu'un client s'abonne au topic exec (UX snappy). */
    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        String dest = StompHeaderAccessor.wrap(event.getMessage()).getDestination();
        if (TOPIC.equals(dest)) {
            // Force le prochain push même si snapshot inchangé (le nouvel abonné
            // n'a encore rien reçu).
            lastPushed = null;
            publishIfChanged();
        }
    }

    @Scheduled(fixedDelayString = "${app.realtime.admin-exec.interval-ms:30000}")
    public void scheduledPublish() {
        if (activeSessions.get() == 0) {
            return; // personne connecté → 0 requête DB
        }
        publishIfChanged();
    }

    private void publishIfChanged() {
        // null period → snapshot plateforme par défaut (le frontend gère le
        // sélecteur de période en fallback REST tant que le WS pousse 1 période).
        AdminStatsFullDto snapshot = statsService.compute(null);
        if (snapshot.equals(lastPushed)) {
            return; // aucun changement → pas de trafic inutile
        }
        lastPushed = snapshot;
        messagingTemplate.convertAndSend(TOPIC, snapshot);
        log.debug("[ws] push {} → {} sessions", TOPIC, activeSessions.get());
    }
}
