package com.onesley.oneclick.modules.system.internal;

import com.onesley.oneclick.realtime.AbstractDashboardPublisher;
import com.onesley.oneclick.realtime.RealtimeFingerprint;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Bug 37 — Publisher temps réel des alertes système / anti-fraude (admin).
 * Empreinte sur {@code system_alerts} → push sur changement → le front invalide
 * les KPI anti-fraude ({@code ["pulse-anti-fraude"]}) et le flux d'alertes
 * ({@code ["admin_fraud_alerts"]}) et re-fetch via REST.
 *
 * <p>{@code system_alerts} n'a pas de colonne {@code updated_at} : la détection
 * combine insertion ET acquittement via {@code greatest(created_at, acknowledged_at)}
 * — un admin qui acquitte une alerte rafraîchit aussi les autres écrans ouverts.
 */
@Component
public class SystemAlertsDashboardPublisher extends AbstractDashboardPublisher<RealtimeFingerprint> {

    public static final String TOPIC = "/topic/admin/alerts";

    @PersistenceContext
    private EntityManager em;

    public SystemAlertsDashboardPublisher(SimpMessagingTemplate messagingTemplate) {
        super(messagingTemplate);
    }

    @Override
    protected String topic() {
        return TOPIC;
    }

    @Override
    protected RealtimeFingerprint computeSnapshot() {
        return RealtimeFingerprint.of(em, "system_alerts", "greatest(created_at, acknowledged_at)");
    }
}
