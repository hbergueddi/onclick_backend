package com.onesley.oneclick.core.notification;

import com.onesley.oneclick.core.identity.User;
import com.onesley.oneclick.shared.events.ReservationCreatedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Listener Spring Modulith — consomme {@link ReservationCreatedEvent} pour créer
 * automatiquement une notification "Reservation reçue" au client.
 *
 * <p>{@link ApplicationModuleListener} = combinaison de :
 * <ul>
 *   <li>{@code @TransactionalEventListener(phase=AFTER_COMMIT)} → l'event n'est livré
 *       qu'après le COMMIT de la tx qui l'a publié (pas de notif si la résa rollback)</li>
 *   <li>{@code @Async} → l'execution est non-bloquante pour le caller (la résa ne ralentit pas)</li>
 *   <li>{@code @Transactional(REQUIRES_NEW)} → la création de la notif est dans sa propre tx</li>
 * </ul>
 *
 * <p>Pattern Phase 2 §21 : le module {@code reservation} ne connaît PAS l'existence
 * du module {@code notification}. Il publie juste un event. Quand on extraira le
 * notification-service, on déplacera ce listener — sans toucher au publisher.
 */
@Component
class ReservationEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventListener.class);
    private static final DateTimeFormatter FR_FMT =
        DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm").withZone(ZoneId.of("Africa/Casablanca"));

    private final NotificationRepository notifRepo;

    @PersistenceContext
    private EntityManager entityManager;

    ReservationEventListener(NotificationRepository notifRepo) {
        this.notifRepo = notifRepo;
    }

    @ApplicationModuleListener
    void onReservationCreated(ReservationCreatedEvent event) {
        log.info("Event consumed [ReservationCreated] reservationId={} clientId={}",
            event.reservationId(), event.clientId());

        User clientRef = entityManager.getReference(User.class, event.clientId());

        // Création d'une notification in-app pour le client
        Notification notif = new Notification(
            UUID.randomUUID(),
            clientRef,
            "reservation",
            "inapp",
            "Réservation reçue",
            "Votre demande de réservation pour " + event.guestCount() + " personnes le "
                + FR_FMT.format(event.reservationAt()) + " est enregistrée."
        );
        notif.setLink("/pocket/reservations/" + event.reservationId());
        notifRepo.save(notif);

        log.debug("Notification created for client={} link=/pocket/reservations/{}",
            event.clientId(), event.reservationId());
    }
}
