package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.realtime.AbstractDashboardPublisher;
import com.onesley.oneclick.realtime.RealtimeFingerprint;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publisher temps réel du <b>dashboard staff des réservations de ressources</b> PCC
 * (Padel / Spa / Golf / Coiffeur / Palm Gym / Tennis / Foot…). Board opérationnel
 * live — <b>0 polling</b> (directive projet : temps réel = WebSocket STOMP exclusif).
 *
 * <p>Calqué sur {@link com.onesley.oneclick.modules.reservation.internal.DisputeDashboardPublisher}
 * + {@link AbstractDashboardPublisher} : empreinte légère sur {@code resource_bookings}
 * → {@code greatest(created_at, updated_at)} (capte à la fois une NOUVELLE réservation
 * — création — et un CHANGEMENT DE STATUT — confirmer/refuser/honorer/no_show/annuler,
 * qui touche {@code updated_at}). Quand l'empreinte change, push STOMP sur
 * {@code /topic/resource-bookings} → le front invalide sa query et re-fetch via REST.</p>
 *
 * <p>En plus du recompute périodique + push-on-subscribe hérités de la classe de base,
 * le {@code ResourceBookingService} appelle {@link #pushNow()} (best-effort, après commit)
 * dès la création d'un booking ET à chaque changement de statut, pour une latence dashboard
 * minimale.</p>
 */
@Component
public class ResourceBookingDashboardPublisher extends AbstractDashboardPublisher<RealtimeFingerprint> {

    public static final String TOPIC = "/topic/resource-bookings";

    @PersistenceContext
    private EntityManager em;

    public ResourceBookingDashboardPublisher(SimpMessagingTemplate messagingTemplate) {
        super(messagingTemplate);
    }

    @Override
    protected String topic() {
        return TOPIC;
    }

    @Override
    protected RealtimeFingerprint computeSnapshot() {
        // Table avec created_at (insertion) + updated_at (changement de statut) : on capte les deux.
        return RealtimeFingerprint.of(em, "resource_bookings", "greatest(created_at, updated_at)");
    }

    /**
     * Push immédiat (création / changement de statut d'un booking) — appelé par le service
     * après mutation. {@code @Transactional(readOnly=true)} : ouvre une session pour la
     * requête native de {@link #computeSnapshot()}.
     */
    @Transactional(readOnly = true)
    public void pushNow() {
        publishIfChanged();
    }
}
