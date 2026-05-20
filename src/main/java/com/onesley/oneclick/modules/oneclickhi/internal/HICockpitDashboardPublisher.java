package com.onesley.oneclick.modules.oneclickhi.internal;

import com.onesley.oneclick.realtime.AbstractDashboardPublisher;
import com.onesley.oneclick.realtime.RealtimeFingerprint;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Bug 37 — Publisher temps réel du cockpit OneClickHI (facturation, admin).
 * Empreinte sur {@code oneclick_hi_invoices} → push sur changement → le front
 * invalide les queries du cockpit HI et re-fetch via REST.
 */
@Component
public class HICockpitDashboardPublisher extends AbstractDashboardPublisher<RealtimeFingerprint> {

    public static final String TOPIC = "/topic/admin/hi-cockpit";

    @PersistenceContext
    private EntityManager em;

    public HICockpitDashboardPublisher(SimpMessagingTemplate messagingTemplate) {
        super(messagingTemplate);
    }

    @Override
    protected String topic() {
        return TOPIC;
    }

    @Override
    protected RealtimeFingerprint computeSnapshot() {
        return RealtimeFingerprint.of(em, "oneclick_hi_invoices");
    }
}
