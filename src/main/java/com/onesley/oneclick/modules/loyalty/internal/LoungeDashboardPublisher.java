package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.realtime.AbstractDashboardPublisher;
import com.onesley.oneclick.realtime.RealtimeFingerprint;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Bug 37 — Publisher temps réel du dashboard OneClick Lounge / fidélité (admin).
 * Empreinte sur {@code loyalty_accounts} → push sur changement → le front
 * invalide {@code ["pulse-lounge-kpi"]} et re-fetch via REST.
 */
@Component
public class LoungeDashboardPublisher extends AbstractDashboardPublisher<RealtimeFingerprint> {

    public static final String TOPIC = "/topic/admin/lounge";

    @PersistenceContext
    private EntityManager em;

    public LoungeDashboardPublisher(SimpMessagingTemplate messagingTemplate) {
        super(messagingTemplate);
    }

    @Override
    protected String topic() {
        return TOPIC;
    }

    @Override
    protected RealtimeFingerprint computeSnapshot() {
        return RealtimeFingerprint.of(em, "loyalty_accounts");
    }
}
