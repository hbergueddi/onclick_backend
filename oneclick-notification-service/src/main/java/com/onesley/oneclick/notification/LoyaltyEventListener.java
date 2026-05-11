package com.onesley.oneclick.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.shared.events.LoyaltyEarnedEvent;
import com.onesley.oneclick.shared.events.LoyaltyRedeemedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Listeners Kafka cross-service — consomment les events de loyalty-service.
 *
 * <p>Phase 3.3 wiring (spec §21) :
 * <ul>
 *   <li>{@code loyalty.earned} → notif "Vous avez gagné X pts"</li>
 *   <li>{@code loyalty.redeemed} → notif "Vous avez utilisé X pts (-Y MAD)"</li>
 * </ul>
 *
 * <p>Conventions identiques à {@link ReservationEventListener} :
 * payload Kafka en {@code byte[]}, parse Jackson manuel pour éviter le double-wrapping
 * Modulith.
 */
@Component
class LoyaltyEventListener {

    private static final Logger log = LoggerFactory.getLogger(LoyaltyEventListener.class);

    private final NotificationRepository notifRepo;
    private final ObjectMapper objectMapper;

    LoyaltyEventListener(NotificationRepository notifRepo, ObjectMapper kafkaEventObjectMapper) {
        this.notifRepo = notifRepo;
        this.objectMapper = kafkaEventObjectMapper;
    }

    @KafkaListener(topics = "loyalty.earned", groupId = "oneclick-notification")
    @Transactional
    void onLoyaltyEarned(byte[] payload) {
        LoyaltyEarnedEvent event;
        try {
            event = objectMapper.readValue(payload, LoyaltyEarnedEvent.class);
        } catch (Exception e) {
            log.error("Parse LoyaltyEarnedEvent failed (len={}): {}", payload.length, e.getMessage(), e);
            return;
        }
        log.info("[Kafka] LoyaltyEarned clientId={} +{} pts", event.clientId(), event.points());

        Notification notif = new Notification(
            UUID.randomUUID(), event.clientId(), "loyalty", "inapp",
            "+%d points fidélité".formatted(event.points()),
            event.reason() != null
                ? event.reason()
                : "Vous avez gagné " + event.points() + " points chez ce restaurant."
        );
        notif.setLink("/pocket/vault");
        notifRepo.save(notif);
    }

    @KafkaListener(topics = "loyalty.redeemed", groupId = "oneclick-notification")
    @Transactional
    void onLoyaltyRedeemed(byte[] payload) {
        LoyaltyRedeemedEvent event;
        try {
            event = objectMapper.readValue(payload, LoyaltyRedeemedEvent.class);
        } catch (Exception e) {
            log.error("Parse LoyaltyRedeemedEvent failed (len={}): {}", payload.length, e.getMessage(), e);
            return;
        }
        log.info("[Kafka] LoyaltyRedeemed clientId={} -{} pts", event.clientId(), event.pointsUsed());

        StringBuilder body = new StringBuilder();
        body.append("Vous avez utilisé ").append(event.pointsUsed()).append(" points");
        if (event.discountAmount() != null) {
            body.append(" (-").append(event.discountAmount()).append(" MAD)");
        }
        body.append(".");

        Notification notif = new Notification(
            UUID.randomUUID(), event.clientId(), "loyalty", "inapp",
            "Réduction appliquée",
            body.toString()
        );
        notif.setLink("/pocket/vault");
        notifRepo.save(notif);
    }
}
