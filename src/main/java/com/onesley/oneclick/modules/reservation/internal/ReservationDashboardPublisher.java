package com.onesley.oneclick.modules.reservation.internal;

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
 * #10 — Publisher temps réel des <b>réservations par-restaurant</b> (écran Calendrier / liste des
 * réservations côté staff). Matérialise la destination {@code /topic/reservations/{restaurantId}}
 * que l'app native (Store) <b>s'abonne déjà</b> ({@code RealtimeTopics.reservations(restaurantId)})
 * mais qui était jusqu'ici inerte faute de publisher Spring.
 *
 * <h3>Temps réel = WebSocket (jamais polling)</h3>
 * <p>À chaque création ou changement de statut de réservation, on pousse un {@link RealtimeSignal}
 * <b>PII-free</b> sur le topic du restaurant concerné. Le front invalide sa query et re-fetch via
 * REST (gardé ABAC par-restaurant) — le topic n'est qu'un déclencheur d'invalidation, calqué sur
 * {@code ResourceBookingDashboardPublisher}.
 *
 * <h3>Découplage (zéro couplage à {@code ReservationService})</h3>
 * <p>On réagit aux events <b>déjà publiés</b> par {@code ReservationService}
 * ({@link ReservationCreatedEvent} / {@link ReservationStatusChangedEvent}, package {@code shared}
 * OPEN) — aucune modification du service, aucune nouvelle migration. Frontière Modulith respectée :
 * la seule entrée est via {@code shared.events}.
 *
 * <h3>Pourquoi {@code @TransactionalEventListener(AFTER_COMMIT)} et pas
 * {@code @ApplicationModuleListener}</h3>
 * <p>Un push WS éphémère « best-effort » doit partir <b>après le commit</b> (sinon le re-fetch REST
 * lirait un état non encore committé), mais ne doit <b>pas</b> être persisté dans
 * {@code event_publication} ni ouvrir une transaction {@code REQUIRES_NEW} (overhead inutile pour un
 * simple ping sans accès DB). {@code @ApplicationModuleListener} (= async + persistance + REQUIRES_NEW)
 * est le bon choix pour la pénalité loyalty durable ({@code ReservationRatingListener}), pas pour un
 * signal d'invalidation jetable. {@code fallbackExecution=true} couvre le cas (rare) d'un event
 * publié hors transaction.
 *
 * <h3>Sécurité STOMP</h3>
 * <p>Aucune règle ajoutée : la destination est sous {@code /topic/...} (non sensible — seul
 * {@code /topic/admin/**} exige SUPERADMIN, cf {@code StompAuthChannelInterceptor}). Le signal est
 * PII-free et la donnée réelle reste protégée par l'autorisation REST.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReservationDashboardPublisher {

    /** Préfixe du topic par-restaurant ; chemin complet = préfixe + {@code restaurantId}. */
    static final String TOPIC_PREFIX = "/topic/reservations/";

    static final String REASON_CREATED = "reservation.created";
    static final String REASON_STATUS_CHANGED = "reservation.status_changed";

    private final SimpMessagingTemplate messagingTemplate;

    /** Topic complet d'un restaurant (public pour réutilisation côté tests / contrat front). */
    public static String topicFor(UUID restaurantId) {
        return TOPIC_PREFIX + restaurantId;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReservationCreated(ReservationCreatedEvent event) {
        publish(event.restaurantId(), REASON_CREATED);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onReservationStatusChanged(ReservationStatusChangedEvent event) {
        publish(event.restaurantId(), REASON_STATUS_CHANGED);
    }

    /** Push best-effort : un échec STOMP ne casse jamais la transaction métier déjà committée. */
    void publish(UUID restaurantId, String reason) {
        if (restaurantId == null) {
            return; // pas de restaurant cible → rien à pousser
        }
        try {
            messagingTemplate.convertAndSend(topicFor(restaurantId), RealtimeSignal.of(restaurantId, reason));
        } catch (RuntimeException e) {
            log.warn("[ws] reservations push échoué (resto={}, reason={}): {}", restaurantId, reason, e.getMessage());
        }
    }
}
