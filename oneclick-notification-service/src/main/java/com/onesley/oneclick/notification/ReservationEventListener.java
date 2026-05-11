package com.onesley.oneclick.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.shared.events.ReservationCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Listener Kafka — consomme {@link ReservationCreatedEvent} pour créer
 * automatiquement une notification "Réservation reçue" au client.
 *
 * <p>Architecture microservice (Phase 2 §21 spec senior dev) :
 * <ul>
 *   <li>Publisher = oneclick-core/modules.reservation (sur commit)</li>
 *   <li>Externalizer = Spring Modulith → JSON bytes → Kafka topic "reservation.created"</li>
 *   <li>Consumer = ce listener (notification-service standalone)</li>
 * </ul>
 *
 * <p>Le payload arrive en {@code byte[]} (cf {@link KafkaConsumerConfig}) — on parse
 * en {@link ReservationCreatedEvent} via Jackson manuellement. Ce détour est dû au
 * fait que Spring Modulith pré-sérialise les events en bytes via son propre
 * {@code JacksonEventSerializer} : on évite le double-wrapping en utilisant
 * ByteArraySerializer/Deserializer aux 2 bouts.
 */
@Component
class ReservationEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventListener.class);
    private static final DateTimeFormatter FR_FMT =
        DateTimeFormatter.ofPattern("dd/MM 'à' HH:mm").withZone(ZoneId.of("Africa/Casablanca"));

    private final NotificationRepository notifRepo;
    private final ObjectMapper objectMapper;

    ReservationEventListener(NotificationRepository notifRepo, ObjectMapper kafkaEventObjectMapper) {
        this.notifRepo = notifRepo;
        this.objectMapper = kafkaEventObjectMapper;
    }

    @KafkaListener(topics = "reservation.created", groupId = "oneclick-notification")
    @Transactional
    void onReservationCreated(byte[] payload) {
        ReservationCreatedEvent event;
        try {
            event = objectMapper.readValue(payload, ReservationCreatedEvent.class);
        } catch (Exception e) {
            log.error("Failed to parse ReservationCreatedEvent from Kafka payload (len={}): {}",
                payload.length, e.getMessage(), e);
            return;
        }

        log.info("[Kafka] Consumed ReservationCreated reservationId={} clientId={}",
            event.reservationId(), event.clientId());

        Notification notif = new Notification(
            UUID.randomUUID(),
            event.clientId(),
            "reservation",
            "inapp",
            "Réservation reçue",
            "Votre demande de réservation pour " + event.guestCount() + " personnes le "
                + FR_FMT.format(event.reservationAt()) + " est enregistrée."
        );
        notif.setLink("/pocket/reservations/" + event.reservationId());
        notifRepo.save(notif);

        log.debug("Notification persisted for client={} → /pocket/reservations/{}",
            event.clientId(), event.reservationId());
    }
}
