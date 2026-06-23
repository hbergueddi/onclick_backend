package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushResultDto;
import com.onesley.oneclick.shared.events.PromoAudienceResolvedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Côté notification du fan-out promo (Sprint R2) : reçoit l'audience résolue par loyalty et
 * pousse le FCM via {@link FcmPushService#sendPromo} (mode stub si {@code app.fcm.*} absents),
 * puis trace le résultat sur la demande ({@code markSent} → statut {@code sent} + compteur).
 *
 * <p><b>B15b</b> : en plus du push FCM (best-effort, peut échouer si pas de token), on crée une
 * <b>ligne in-app</b> ({@code type='promotion'}, lien Promos) pour CHAQUE user du segment résolu —
 * ainsi la promo apparaît dans la cloche même sans token push. Ce sont des destinataires CLIENTS
 * (segment marketing) → <b>pas de filtre staff</b> (les préférences V85 ne concernent que le staff).
 * On itère sur la même liste que le push pour rester économe sur les gros segments.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PromoDispatchListener {

    private final FcmPushService pushService;
    private final PromoNotificationService promoService;
    /** B15b — création des lignes in-app (cloche) par user du segment. */
    private final NotificationService notificationService;

    @ApplicationModuleListener
    public void onPromoAudienceResolved(PromoAudienceResolvedEvent event) {
        if (event.userIds() == null || event.userIds().isEmpty()) {
            markSentBestEffort(event.requestId(), 0, "no recipients");
            log.info("[promo-push] demande {} : 0 destinataire, rien à pousser", event.requestId());
            return;
        }
        // B15b — ligne in-app par user du segment (en plus du push). Même itération que le push pour
        // ne pas doubler la pression mémoire sur les gros segments. Best-effort par user :
        // NotificationService.create est déjà tolérant (garde-fou FK + try/catch interne minimal),
        // on englobe quand même pour ne JAMAIS faire échouer le push à cause d'un in-app.
        int inApp = 0;
        for (UUID userId : event.userIds()) {
            try {
                notificationService.create(new NotificationCreateDto(
                    userId, "promotion", "inapp", event.title(), event.body(), event.link()));
                inApp++;
            } catch (Exception ex) {
                log.warn("[promo-inapp] demande {} : in-app échouée pour user {} : {}",
                    event.requestId(), userId, ex.getMessage());
            }
        }
        log.info("[promo-inapp] demande {} : {} lignes in-app créées sur {} destinataires",
            event.requestId(), inApp, event.userIds().size());

        // Push FCM best-effort : un échec d'envoi (ou un nettoyage de token périmé qui dérape pendant
        // le fan-out) ne doit JAMAIS faire planter ce listener. Sinon l'event Modulith reste INCOMPLET
        // (rejoué en boucle au restart) et la demande reste coincée en 'approved' (jamais 'sent'),
        // sans in-app ni push livrés. Calque du pattern résa (NotificationEventHandler.notify : push
        // entouré d'un try/catch). markSent est lui aussi best-effort (cf markSentBestEffort).
        int sent = 0;
        int total = event.userIds().size();
        String error;
        try {
            PushResultDto result = pushService.sendPromo(new PushPromoDto(
                event.requestId(), event.userIds(), event.title(), event.body(), event.link()));
            sent = result.sent();
            total = result.total();
            error = result.sent() == result.total() ? null : result.message();
            log.info("[promo-push] demande {} : {}/{} push envoyés ({})",
                event.requestId(), result.sent(), result.total(), result.message());
        } catch (Exception ex) {
            error = "push failed: " + ex.getMessage();
            log.warn("[promo-push] demande {} : envoi FCM échoué (in-app déjà créées) : {}",
                event.requestId(), ex.getMessage());
        }
        markSentBestEffort(event.requestId(), sent, error);
    }

    /**
     * Passe la demande en {@code sent} de façon <b>best-effort</b> : une exception de mise à jour de
     * statut ne doit pas faire échouer le listener (l'essentiel — in-app + push — est déjà tenté).
     * Évite qu'un incident sur {@code markSent} laisse l'event Modulith incomplet (rejoué en boucle).
     */
    private void markSentBestEffort(UUID requestId, int sent, String error) {
        try {
            promoService.markSent(requestId, sent, error);
        } catch (Exception ex) {
            log.warn("[promo-push] demande {} : markSent échoué : {}", requestId, ex.getMessage());
        }
    }
}
