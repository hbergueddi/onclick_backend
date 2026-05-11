package com.onesley.oneclick.loyalty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.shared.events.PaymentSucceededEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listener Kafka — consomme {@code payment.succeeded} pour traiter side-effects loyalty.
 *
 * <p>Phase 3.3 wiring (spec §21) :
 * {@code payment-service} → {@code payment.succeeded} → ici on logge l'event.
 *
 * <p>Évolution Phase 11 : si le payment référence un restaurant (referenceType="restaurant"),
 * créditer automatiquement les points fidélité au client.
 * Pour l'instant on logge seulement (l'EF Supabase {@code snap2earn} reste la voie principale
 * pour l'octroi de points via tickets scannés).
 */
@Component
class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final ObjectMapper objectMapper;

    PaymentEventListener(ObjectMapper kafkaEventObjectMapper) {
        this.objectMapper = kafkaEventObjectMapper;
    }

    @KafkaListener(topics = "payment.succeeded", groupId = "oneclick-loyalty")
    void onPaymentSucceeded(byte[] payload) {
        PaymentSucceededEvent event;
        try {
            event = objectMapper.readValue(payload, PaymentSucceededEvent.class);
        } catch (Exception e) {
            log.error("Parse PaymentSucceededEvent failed (len={}): {}", payload.length, e.getMessage(), e);
            return;
        }
        log.info("[Kafka@loyalty] PaymentSucceeded paymentId={} userId={} amount={} refType={} refId={}",
            event.paymentId(), event.userId(), event.amount(),
            event.referenceType(), event.referenceId());

        // TODO Phase 11 : if (event.referenceType().equals("restaurant")) {
        //     LoyaltyService.earnFromPayment(event.userId(), event.referenceId(), event.amount());
        // }
    }
}
