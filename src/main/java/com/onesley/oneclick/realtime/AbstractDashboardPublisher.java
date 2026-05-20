package com.onesley.oneclick.realtime;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bug 37 — Base réutilisable des publishers de dashboard temps réel (rollout WS).
 *
 * <p>Factorise le pattern « agrégat » validé sur le dashboard exec :
 * <ul>
 *   <li>recompute périodique ({@code @Scheduled}) borné dans le temps,</li>
 *   <li>push UNIQUEMENT si le snapshot a changé ({@code equals}),</li>
 *   <li>gate sur la présence d'au moins 1 session WS (0 charge DB à vide),</li>
 *   <li>push immédiat à l'abonnement au topic (UX).</li>
 * </ul>
 *
 * <p>Une sous-classe = un dashboard : elle fournit {@link #topic()} et
 * {@link #computeSnapshot()} (réutilise le service d'agrégat de son module).
 * Les {@code @EventListener}/{@code @Scheduled} hérités sont enregistrés par
 * Spring sur chaque bean concret (scan des méthodes héritées).
 *
 * <p>Intervalle commun configurable via {@code app.realtime.dashboard.interval-ms}
 * (défaut 30s). Le snapshot {@code T} DOIT être un type à {@code equals} par
 * valeur (record) pour la détection de changement.
 *
 * @param <T> type du snapshot poussé (record sérialisable Jackson)
 */
public abstract class AbstractDashboardPublisher<T> {

    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicInteger activeSessions = new AtomicInteger(0);
    private volatile T lastPushed;

    protected AbstractDashboardPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** Destination STOMP du dashboard (ex: {@code /topic/admin/fraud}). */
    protected abstract String topic();

    /** Calcule le snapshot courant (agrégat du module). Doit être un record. */
    protected abstract T computeSnapshot();

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        activeSessions.incrementAndGet();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        activeSessions.updateAndGet(n -> Math.max(0, n - 1));
    }

    /**
     * Push immédiat dès qu'un client s'abonne à CE topic (UX snappy).
     *
     * <p>{@code @Transactional(readOnly=true)} : appelé par le multicaster d'events
     * (donc via le proxy) → ouvre une session pour les requêtes natives de
     * {@link #computeSnapshot()} (fingerprints).
     */
    @EventListener
    @Transactional(readOnly = true)
    public void onSubscribe(SessionSubscribeEvent event) {
        String dest = StompHeaderAccessor.wrap(event.getMessage()).getDestination();
        if (topic().equals(dest)) {
            lastPushed = null; // force le push pour le nouvel abonné
            publishIfChanged();
        }
    }

    @Scheduled(fixedDelayString = "${app.realtime.dashboard.interval-ms:30000}")
    @Transactional(readOnly = true)
    public void scheduledPublish() {
        if (activeSessions.get() == 0) {
            return; // personne connecté → 0 requête DB
        }
        publishIfChanged();
    }

    protected final void publishIfChanged() {
        T snapshot = computeSnapshot();
        if (snapshot == null || snapshot.equals(lastPushed)) {
            return; // aucun changement → pas de trafic inutile
        }
        lastPushed = snapshot;
        messagingTemplate.convertAndSend(topic(), snapshot);
    }
}
