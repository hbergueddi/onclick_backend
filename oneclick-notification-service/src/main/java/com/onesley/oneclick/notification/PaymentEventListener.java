package com.onesley.oneclick.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.shared.events.PaymentSucceededEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Listener Kafka — consomme {@code payment.succeeded} pour notifier le client.
 *
 * <p>Phase 3.3 wiring (spec §21) :
 * {@code payment-service} → {@code payment.succeeded} → notif "Paiement confirmé".
 */
@Component
class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final NotificationRepository notifRepo;
    private final ObjectMapper objectMapper;

    PaymentEventListener(NotificationRepository notifRepo, ObjectMapper kafkaEventObjectMapper) {
        this.notifRepo = notifRepo;
        this.objectMapper = kafkaEventObjectMapper;
    }

    @KafkaListener(topics = "payment.succeeded", groupId = "oneclick-notification")
    @Transactional
    void onPaymentSucceeded(byte[] payload) {
        PaymentSucceededEvent event;
        try {
            event = objectMapper.readValue(payload, PaymentSucceededEvent.class);
        } catch (Exception e) {
            log.error("Parse PaymentSucceededEvent failed (len={}): {}", payload.length, e.getMessage(), e);
            return;
        }
        log.info("[Kafka] PaymentSucceeded paymentId={} userId={} amount={}",
            event.paymentId(), event.userId(), event.amount());

        Notification notif = new Notification(
            UUID.randomUUID(),
            event.userId(),
            "payment",
            "inapp",
            "Paiement confirmé",
            "Votre paiement de %s %s via %s a été confirmé.".formatted(
                event.amount(), event.currency(),
                event.provider() == null ? "carte" : event.provider()
            )
        );
        if (event.referenceType() != null && event.referenceId() != null) {
            notif.setLink("/pocket/" + event.referenceType() + "/" + event.referenceId());
        }
        notifRepo.save(notif);
    }
}
