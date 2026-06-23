package com.onesley.oneclick.modules.restaurant_referral.internal;

import com.onesley.oneclick.realtime.AbstractDashboardPublisher;
import com.onesley.oneclick.realtime.RealtimeFingerprint;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher temps réel du dashboard ADMIN des parrainages restaurant-à-restaurant.
 *
 * <p>Calque {@code ReferralDashboardPublisher} (parrainage CLIENT) : empreinte
 * {@code (rowCount, max(updated_at))} sur {@code restaurant_referrals} → push STOMP sur changement →
 * le front invalide sa query et re-fetch via REST. Pas de polling.
 *
 * <p>Le {@code RestaurantReferralService} appelle {@link #pushNow()} juste après une activation pour
 * un push immédiat (en plus du recompute périodique hérité de {@link AbstractDashboardPublisher}).
 */
@Component
public class RestaurantReferralDashboardPublisher extends AbstractDashboardPublisher<RealtimeFingerprint> {

    public static final String TOPIC = "/topic/admin/restaurant-referrals";

    @PersistenceContext
    private EntityManager em;

    public RestaurantReferralDashboardPublisher(SimpMessagingTemplate messagingTemplate) {
        super(messagingTemplate);
    }

    @Override
    protected String topic() {
        return TOPIC;
    }

    @Override
    protected RealtimeFingerprint computeSnapshot() {
        return RealtimeFingerprint.of(em, "restaurant_referrals");
    }

    /**
     * Push immédiat post-mutation (best-effort) : pousse uniquement si l'empreinte a changé. Appelé
     * par le service après une activation pour éviter d'attendre le tick périodique.
     */
    public void pushNow() {
        publishIfChanged();
    }
}
