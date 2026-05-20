package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.realtime.AbstractDashboardPublisher;
import com.onesley.oneclick.realtime.RealtimeFingerprint;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Bug 37 — Publisher temps réel du dashboard Parrainage (admin).
 * Empreinte sur {@code referrals} → push sur changement → le front invalide
 * {@code ["pulse-parrainage"]} et re-fetch via REST.
 */
@Component
public class ReferralDashboardPublisher extends AbstractDashboardPublisher<RealtimeFingerprint> {

    public static final String TOPIC = "/topic/admin/referral";

    @PersistenceContext
    private EntityManager em;

    public ReferralDashboardPublisher(SimpMessagingTemplate messagingTemplate) {
        super(messagingTemplate);
    }

    @Override
    protected String topic() {
        return TOPIC;
    }

    @Override
    protected RealtimeFingerprint computeSnapshot() {
        return RealtimeFingerprint.of(em, "referrals");
    }
}
