package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.realtime.AbstractDashboardPublisher;
import com.onesley.oneclick.realtime.RealtimeFingerprint;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feature #3 — Publisher temps réel du dashboard de contestations no-show
 * (resto/support). Empreinte sur {@code no_show_disputes} →
 * {@code greatest(created_at, resolved_at)} : capte à la fois une NOUVELLE
 * contestation (création) et une RÉSOLUTION (resolved_at posé) → push STOMP
 * sur {@code /topic/disputes} → le front invalide sa query et re-fetch via REST
 * (pas de polling).
 *
 * <p>En plus du recompute périodique + push-on-subscribe hérités de
 * {@link AbstractDashboardPublisher}, le {@code DisputeController} appelle
 * {@link #pushNow()} immédiatement après une création / résolution pour une
 * latence dashboard minimale.
 */
@Component
public class DisputeDashboardPublisher extends AbstractDashboardPublisher<RealtimeFingerprint> {

    public static final String TOPIC = "/topic/disputes";

    @PersistenceContext
    private EntityManager em;

    public DisputeDashboardPublisher(SimpMessagingTemplate messagingTemplate) {
        super(messagingTemplate);
    }

    @Override
    protected String topic() {
        return TOPIC;
    }

    @Override
    protected RealtimeFingerprint computeSnapshot() {
        // Table append-only + résolution (pas d'updated_at) : on capte création ET résolution.
        return RealtimeFingerprint.of(em, "no_show_disputes", "greatest(created_at, resolved_at)");
    }

    /**
     * Push immédiat (création / résolution d'une contestation) — appelé par le
     * contrôleur après mutation. {@code @Transactional(readOnly=true)} : ouvre une
     * session pour la requête native de {@link #computeSnapshot()}.
     */
    @Transactional(readOnly = true)
    public void pushNow() {
        publishIfChanged();
    }
}
