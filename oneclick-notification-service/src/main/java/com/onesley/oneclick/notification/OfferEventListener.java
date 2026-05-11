package com.onesley.oneclick.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onesley.oneclick.shared.events.OfferCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Listener Kafka — consomme {@code offer.created} pour pousser une campagne notification.
 *
 * <p>Phase 3.3 wiring (spec §21) :
 * core/promotion publie quand un resto crée une promo → ici on crée une
 * {@link NotificationCampaign} qui sera dispatchée vers tous les clients
 * abonnés au resto (job de fan-out à wirer en Phase 11 push notifications).
 *
 * <p>Pattern : pas de fan-out direct (peut être 1000+ destinataires).
 * On enregistre la campagne, un job background (Phase 11 send-promo-push EF)
 * fait le batching et l'envoi FCM.
 */
@Component
class OfferEventListener {

    private static final Logger log = LoggerFactory.getLogger(OfferEventListener.class);

    private final NotificationCampaignRepository campaignRepo;
    private final ObjectMapper objectMapper;

    OfferEventListener(NotificationCampaignRepository campaignRepo, ObjectMapper kafkaEventObjectMapper) {
        this.campaignRepo = campaignRepo;
        this.objectMapper = kafkaEventObjectMapper;
    }

    @KafkaListener(topics = "offer.created", groupId = "oneclick-notification")
    @Transactional
    void onOfferCreated(byte[] payload) {
        OfferCreatedEvent event;
        try {
            event = objectMapper.readValue(payload, OfferCreatedEvent.class);
        } catch (Exception e) {
            log.error("Parse OfferCreatedEvent failed (len={}): {}", payload.length, e.getMessage(), e);
            return;
        }
        log.info("[Kafka] OfferCreated offerId={} restaurantId={} '{}'",
            event.offerId(), event.restaurantId(), event.title());

        // Crée une campaign à dispatcher (le push batch sera fait en Phase 11)
        NotificationCampaign campaign = new NotificationCampaign(
            UUID.randomUUID(),
            event.tenantId(),
            "Nouvelle offre : " + event.title(),
            event.description() != null ? event.description() : event.title()
        );
        campaign.setStatus("scheduled");
        campaign.setScheduledAt(event.startsAt() != null ? event.startsAt() : Instant.now());
        campaign.setTargetSegment("restaurant:" + event.restaurantId());
        campaignRepo.save(campaign);
    }
}
