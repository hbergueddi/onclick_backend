package com.onesley.oneclick.modules.analytics.internal;

import com.onesley.oneclick.realtime.RealtimeSignal;
import com.onesley.oneclick.shared.events.ReservationCreatedEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * #10 — Publisher temps réel du <b>dashboard restaurateur par-restaurant</b> (PulsePro / Cockpit
 * staff). Matérialise la destination {@code /topic/dashboard/{restaurantId}} que l'app native (Store)
 * <b>s'abonne déjà</b> ({@code RealtimeTopics.dashboard(restaurantId)}) mais qui était inerte faute
 * de publisher Spring.
 *
 * <h3>Bounded context</h3>
 * <p>Le module {@code analytics} possède le read-model des dashboards (cf {@code AdminStatsService},
 * {@code TenantKpisPublisher}, {@code GroupDashboardPublisher}) ; le dashboard <b>restaurateur</b>
 * y a donc naturellement sa place. Comme ses KPIs (réservations du jour, taux d'honoration, no-shows)
 * sont pilotés par le cycle de vie des réservations, on réagit aux events
 * {@link ReservationCreatedEvent} / {@link ReservationStatusChangedEvent} (package {@code shared}
 * OPEN, déjà consommé ainsi par {@code loyalty.ReservationRatingListener}). Zéro couplage direct
 * {@code reservation → analytics}, zéro migration.
 *
 * <h3>Temps réel = WebSocket, signal PII-free</h3>
 * <p>On pousse un {@link RealtimeSignal} sans donnée sensible : le front invalide sa query dashboard
 * et re-fetch via REST (scopé serveur). Même design que {@code ReservationDashboardPublisher} et
 * {@code ResourceBookingDashboardPublisher}.
 *
 * <h3>{@code @TransactionalEventListener(AFTER_COMMIT)} (pas {@code @ApplicationModuleListener})</h3>
 * <p>Push éphémère best-effort après commit (re-fetch sur état committé), sans persistance
 * {@code event_publication} ni transaction {@code REQUIRES_NEW} superflue ;
 * {@code fallbackExecution=true} pour l'event publié hors transaction.
 *
 * <h3>Sécurité STOMP</h3>
 * <p>Destination sous {@code /topic/...} (non sensible — seul {@code /topic/admin/**} exige
 * SUPERADMIN). Signal PII-free, données réelles protégées par l'autorisation REST.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RestaurantDashboardPublisher {

    /** Préfixe du topic dashboard par-restaurant ; chemin complet = préfixe + {@code restaurantId}. */
    static final String TOPIC_PREFIX = "/topic/dashboard/";

    static final String REASON_RESERVATION_CREATED = "reservation.created";
    static final String REASON_RESERVATION_STATUS_CHANGED = "reservation.status_changed";

    private final SimpMessagingTemplate messagingTemplate;

    /** Topic dashboard complet d'un restaurant (public pour réutilisation côté tests / contrat front). */
    public static String topicFor(UUID restaurantId) {
        return TOPIC_PREFIX + restaurantId;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReservationCreated(ReservationCreatedEvent event) {
        publish(event.restaurantId(), REASON_RESERVATION_CREATED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReservationStatusChanged(ReservationStatusChangedEvent event) {
        publish(event.restaurantId(), REASON_RESERVATION_STATUS_CHANGED);
    }

    /** Push best-effort : un échec STOMP ne casse jamais la transaction métier déjà committée. */
    void publish(UUID restaurantId, String reason) {
        if (restaurantId == null) {
            return; // pas de restaurant cible → rien à pousser
        }
        try {
            messagingTemplate.convertAndSend(topicFor(restaurantId), RealtimeSignal.of(restaurantId, reason));
        } catch (RuntimeException e) {
            log.warn("[ws] dashboard push échoué (resto={}, reason={}): {}", restaurantId, reason, e.getMessage());
        }
    }
}
