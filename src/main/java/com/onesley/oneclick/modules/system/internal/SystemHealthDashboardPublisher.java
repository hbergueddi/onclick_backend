package com.onesley.oneclick.modules.system.internal;

import com.onesley.oneclick.realtime.AbstractDashboardPublisher;
import com.onesley.oneclick.realtime.RealtimeFingerprint;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Audit R3 — Publisher temps réel des métriques de santé système (dashboard /system).
 *
 * <p>Directive #1 (temps réel = WebSocket) : remplace le {@code refetchInterval}
 * front (polling 60 s de {@code useHealthMetrics}) par un push STOMP. La périodicité
 * passe côté serveur ({@code @Scheduled} hérité de {@link AbstractDashboardPublisher},
 * borné par le flag « ≥ 1 abonné »), et le front invalide {@code ["system-health-metrics"]}
 * à chaque changement pour re-fetch via REST.
 *
 * <p>Empreinte sur {@code system_health_checks} via {@code max(checked_at)} (table
 * append-only : pas de colonne {@code updated_at}). Topic admin gated SUPERADMIN par
 * {@code StompAuthChannelInterceptor} (préfixe {@code /topic/admin/**}). Même pattern
 * que {@link SystemAlertsDashboardPublisher}.
 */
@Component
public class SystemHealthDashboardPublisher extends AbstractDashboardPublisher<RealtimeFingerprint> {

    public static final String TOPIC = "/topic/admin/system-health";

    @PersistenceContext
    private EntityManager em;

    public SystemHealthDashboardPublisher(SimpMessagingTemplate messagingTemplate) {
        super(messagingTemplate);
    }

    @Override
    protected String topic() {
        return TOPIC;
    }

    @Override
    protected RealtimeFingerprint computeSnapshot() {
        return RealtimeFingerprint.of(em, "system_health_checks", "checked_at");
    }
}
