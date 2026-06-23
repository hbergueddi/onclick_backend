package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.shared.events.NoShowDisputeResolvedEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Listener loyalty sur les events réservation — applique la <b>réputation client</b>
 * (Feature #4 + Feature #3) sans dépendance directe {@code reservation → loyalty}.
 *
 * <p>C'est le module loyalty (propriétaire de {@code client_ratings} +
 * {@code client_score_config}) qui réagit aux events publiés par le module
 * reservation. Frontière Modulith respectée : la seule communication entrante est
 * via {@link com.onesley.oneclick.shared.events} (package OPEN). Aucun appel direct
 * {@code ReservationService → LoyaltyExtensionService}.
 *
 * <h3>Règles</h3>
 * <ul>
 *   <li>{@code status → no_show} : {@code recordRating(-penaliteNoShow, "no_show")} —
 *       le client est pénalisé. {@code penaliteNoShow} vient de la config singleton
 *       {@link ClientScoreConfig} (V52, défaut 0.5 depuis V102 — parité legacy).</li>
 *   <li>{@code status → honored} : {@code recordRating(+gainParPalier, "honored")} —
 *       le client remonte sa note. {@code gainParPalier} (défaut 0.1).</li>
 *   <li>Contestation {@code accepted} ({@link NoShowDisputeResolvedEvent}) :
 *       {@code recordRating(+penaliteNoShow, "dispute_accepted")} — on <b>reverse</b>
 *       la pénalité no_show. {@code refused} : aucune action (pénalité conservée).</li>
 * </ul>
 *
 * <p>Async + after-commit : {@code @ApplicationModuleListener} = {@code @Async} +
 * {@code @Transactional(REQUIRES_NEW)} + {@code @TransactionalEventListener(AFTER_COMMIT)}.
 * La pénalité est donc appliquée une fois la transition de statut committée.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReservationRatingListener {

    private final LoyaltyExtensionService loyaltyExtensionService;
    private final ClientScoreConfigRepository scoreConfigRepo;

    @ApplicationModuleListener
    public void onReservationStatusChanged(ReservationStatusChangedEvent event) {
        if (event.clientId() == null) {
            log.warn("[loyalty-rating] clientId null (resa={}) — skip", event.reservationId());
            return;
        }
        switch (event.newStatus()) {
            case "no_show" -> applyRating(event, penaliteNoShow().negate(), "no_show");
            case "honored" -> applyRating(event, gainParPalier(), "honored");
            default -> { /* autres statuts : pas d'impact réputation */ }
        }
    }

    /**
     * Reversal de la pénalité no_show quand une contestation est acceptée.
     * {@code refused} ne publie pas cet event avec {@code accepted=true} → pénalité conservée.
     */
    @ApplicationModuleListener
    public void onDisputeResolved(NoShowDisputeResolvedEvent event) {
        if (!event.accepted()) {
            return; // contestation refusée → on garde la pénalité
        }
        if (event.clientId() == null) {
            log.warn("[loyalty-rating] dispute clientId null (resa={}) — skip", event.reservationId());
            return;
        }
        applyRating(event.clientId(), event.reservationId(), penaliteNoShow(), "dispute_accepted");
    }

    private void applyRating(ReservationStatusChangedEvent event, BigDecimal delta, String reason) {
        applyRating(event.clientId(), event.reservationId(), delta, reason);
    }

    private void applyRating(java.util.UUID clientId, java.util.UUID reservationId, BigDecimal delta, String reason) {
        try {
            loyaltyExtensionService.recordRating(clientId, reservationId, delta, reason);
            log.info("[loyalty-rating] {} appliqué (client={}, resa={}, delta={})",
                reason, clientId, reservationId, delta);
        } catch (Exception ex) {
            log.warn("[loyalty-rating] échec recordRating (client={}, resa={}, reason={}): {}",
                clientId, reservationId, reason, ex.getMessage());
        }
    }

    private ClientScoreConfig config() {
        return scoreConfigRepo.findFirstByOrderByCreatedAtAsc()
            .orElseGet(() -> scoreConfigRepo.save(new ClientScoreConfig()));
    }

    private BigDecimal penaliteNoShow() {
        return config().getPenaliteNoShow();
    }

    private BigDecimal gainParPalier() {
        return config().getGainParPalier();
    }
}
