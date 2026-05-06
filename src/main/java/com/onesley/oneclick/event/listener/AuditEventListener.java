package com.onesley.oneclick.event.listener;

import com.onesley.oneclick.event.DomainEvent;
import com.onesley.oneclick.event.LoyaltyPointsEarnedEvent;
import com.onesley.oneclick.event.ReservationCreatedEvent;
import com.onesley.oneclick.event.ReservationStatusChangedEvent;
import com.onesley.oneclick.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener générique d'audit — journalise toutes les transitions métier.
 *
 * <p>Pattern important : {@link TransactionalEventListener} avec
 * {@code AFTER_COMMIT} garantit qu'on ne logue PAS si la transaction métier a
 * rollback. C'est essentiel pour ne pas avoir des entrées d'audit décrivant
 * des actions qui ne se sont jamais produites en DB.
 *
 * <p>En Phase 11+, ce listener écrira dans {@code admin_audit_log} (table déjà
 * mappée par le scaffolder Phase 4). Pour l'instant on log juste — le pattern
 * de wiring est validé.
 *
 * <p>Pour suivre l'ensemble des events sans déclarer 1 méthode par type, on a
 * un listener générique sur {@link DomainEvent} en plus des handlers typés.
 * Spring résout l'ambiguïté en appelant les deux (le générique pour audit, le
 * typé pour la business logic).
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @EventListener
    public void onAnyDomainEvent(DomainEvent event) {
        log.info("[AUDIT] {} {} {} — eventId={} at={}",
            event.aggregateType(),
            event.aggregateId(),
            event.getClass().getSimpleName(),
            event.eventId(),
            event.occurredAt());
        // TODO Phase 11 : INSERT INTO admin_audit_log via AdminAuditLogRepository
    }

    // Quelques handlers typés pour démonstration — useful pour des audits riches
    // ou des metrics par type d'event. Pas de duplication — chaque event est traité
    // une seule fois par méthode (Spring déduplique sur le type d'event).

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("[AUDIT] user_registered userId={} email={} tenant={} referredBy={}",
            event.userId(), event.email(), event.tenantId(), event.referredById());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationCreated(ReservationCreatedEvent event) {
        log.info("[AUDIT] reservation_created id={} client={} restaurant={} {} {} ({} pers.)",
            event.reservationId(), event.clientId(), event.restaurantId(),
            event.date(), event.heure(), event.couverts());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationStatusChanged(ReservationStatusChangedEvent event) {
        log.info("[AUDIT] reservation_status_changed id={} {} → {} by={} reason={}",
            event.reservationId(),
            event.previousStatus(), event.newStatus(),
            event.changedBy(),
            event.reason());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLoyaltyPointsEarned(LoyaltyPointsEarnedEvent event) {
        log.info("[AUDIT] points_earned client={} restaurant={} +{} pts ({} MAD) source={}",
            event.clientId(), event.restaurantId(),
            event.points(), event.amountTtc(), event.source());
    }
}
