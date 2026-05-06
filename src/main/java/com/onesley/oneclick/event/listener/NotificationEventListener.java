package com.onesley.oneclick.event.listener;

import com.onesley.oneclick.entity.shared.ReservationStatus;
import com.onesley.oneclick.event.LoyaltyPointsEarnedEvent;
import com.onesley.oneclick.event.ReservationCreatedEvent;
import com.onesley.oneclick.event.ReservationStatusChangedEvent;
import com.onesley.oneclick.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener push notifications + email — placeholder Phase 5.3.
 *
 * <p>En Phase 11+ ce listener appellera :
 * <ul>
 *   <li>FCM HTTP v1 API (push notifications iOS/Android)</li>
 *   <li>Resend HTTP API (emails branded)</li>
 *   <li>Une table {@code notifications} pour l'in-app feed</li>
 * </ul>
 *
 * <p>Annotations clés :
 * <ul>
 *   <li>{@code @TransactionalEventListener(AFTER_COMMIT)} — n'envoie que si la
 *       transaction métier a réussi (pas de push pour une réservation rollback)</li>
 *   <li>{@code @Async} — découple l'envoi du flow business pour ne pas bloquer
 *       le retour HTTP. Nécessite {@code @EnableAsync} qu'on activera en
 *       Phase 11+ avec un {@code Executor} dédié (thread pool, retry, etc.).
 *       Pour l'instant {@code @Async} est sans effet (pas d'EnableAsync) — on
 *       garde l'annotation pour documenter l'intention future.</li>
 * </ul>
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendWelcomeEmail(UserRegisteredEvent event) {
        log.info("[NOTIF] welcome email to {} ({} {})",
            event.email(), event.firstName(), event.lastName());
        // TODO Phase 11 : POST https://api.resend.com/emails
        //   { from: "noreply@app-oneclick.net", to: event.email(), template: "welcome", ... }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyRestaurantOfNewReservation(ReservationCreatedEvent event) {
        log.info("[NOTIF] new reservation push → restaurant {} (resa {})",
            event.restaurantId(), event.reservationId());
        // TODO Phase 11 : push FCM vers les staff du restaurant + insert in-app notification
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyClientOfStatusChange(ReservationStatusChangedEvent event) {
        // Filtrage : on ne notifie le client que sur les transitions visibles
        // côté Pocket (confirmée, refusée, contre_proposition, annulée par resto).
        switch (event.newStatus()) {
            case confirmée, refusée, contre_proposition -> {
                log.info("[NOTIF] reservation status push → client {} : {} (resa {})",
                    event.clientId(), event.newStatus(), event.reservationId());
            }
            case ReservationStatus s -> {
                // honorée, no_show, en_attente, demandée, placée, terminée, annulée :
                // soit pas de notification client requise, soit gérée ailleurs (no-show
                // dispute system Session 37). On no-op ici.
            }
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void notifyClientOfPointsEarned(LoyaltyPointsEarnedEvent event) {
        log.info("[NOTIF] points push → client {} +{} pts ({})",
            event.clientId(), event.points(), event.source());
        // TODO Phase 11 : push FCM "Vous avez gagné {points} points chez {restaurant}!"
    }
}
