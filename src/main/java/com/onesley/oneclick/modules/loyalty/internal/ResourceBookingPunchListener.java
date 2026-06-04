package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.shared.events.ResourceBookingStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Listener loyalty sur les changements de statut d'une réservation de RESSOURCE —
 * applique le <b>+1 punch</b> de fidélité (PCC Lot 3) sans dépendance directe
 * {@code resource_booking → loyalty}.
 *
 * <p>C'est le module loyalty (propriétaire de {@code loyalty_punch_cards}) qui réagit
 * à l'event {@link ResourceBookingStatusChangedEvent} publié par le module
 * {@code resource_booking}. Frontière Modulith respectée : la seule communication
 * entrante est via {@link com.onesley.oneclick.shared.events} (package OPEN). Aucun appel
 * direct {@code ResourceBookingService → PunchCardService}.
 *
 * <h3>Règle</h3>
 * <ul>
 *   <li>{@code newStatus == "completed"} (booking HONORÉ) → mappe {@code resourceType}
 *       vers une activité de carte ({@link PunchCardService#resolveActivity(String)}) ; si
 *       le type est mappé, {@code punchCardService.punch(tenantId, organizerId, activity)}.</li>
 *   <li>Tout autre statut, ou un {@code resourceType} non mappé → aucun punch.</li>
 * </ul>
 *
 * <p>Async + after-commit : {@code @ApplicationModuleListener} = {@code @Async} +
 * {@code @Transactional(REQUIRES_NEW)} + {@code @TransactionalEventListener(AFTER_COMMIT)}.
 * Le punch est donc appliqué une fois la transition de statut du booking committée.</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ResourceBookingPunchListener {

    private final PunchCardService punchCardService;

    @ApplicationModuleListener
    public void onStatusChanged(ResourceBookingStatusChangedEvent event) {
        if (!"completed".equals(event.newStatus())) {
            return; // seul un booking honoré déclenche un punch
        }
        if (event.tenantId() == null || event.organizerId() == null) {
            log.warn("[punch-card] event completed sans tenant/organizer (booking={}) — skip",
                event.bookingId());
            return;
        }
        PunchCardService.resolveActivity(event.resourceType()).ifPresentOrElse(
            activity -> {
                try {
                    punchCardService.punch(event.tenantId(), event.organizerId(), activity);
                } catch (Exception ex) {
                    log.warn("[punch-card] échec punch (booking={}, organizer={}, activity={}): {}",
                        event.bookingId(), event.organizerId(), activity, ex.getMessage());
                }
            },
            () -> log.debug("[punch-card] resourceType '{}' non mappé (booking={}) — pas de punch",
                event.resourceType(), event.bookingId())
        );
    }
}
