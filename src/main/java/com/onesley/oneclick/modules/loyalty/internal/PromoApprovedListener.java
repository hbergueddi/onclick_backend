package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.shared.events.PromoApprovedEvent;
import com.onesley.oneclick.shared.events.PromoAudienceResolvedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Côté loyalty du fan-out promo (Sprint R2) : sur approbation d'une demande de push, résout
 * l'audience du segment ({@link PromoAudienceResolver}, données loyalty) puis republie un
 * {@link PromoAudienceResolvedEvent} consommé par notification pour le push FCM.
 *
 * <p>{@code @ApplicationModuleListener} = async + after-commit : la résolution (et donc le push)
 * ne part qu'une fois la demande réellement passée à {@code approved} en base.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PromoApprovedListener {

    private final PromoAudienceResolver resolver;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener
    public void onPromoApproved(PromoApprovedEvent event) {
        List<UUID> userIds = resolver.resolve(event.restaurantId(), event.segment());
        log.info("[promo-audience] demande {} segment '{}' → {} destinataire(s)",
            event.requestId(), event.segment(), userIds.size());
        events.publishEvent(new PromoAudienceResolvedEvent(
            event.requestId(), userIds, event.title(), event.body(), event.link(), Instant.now()));
    }
}
