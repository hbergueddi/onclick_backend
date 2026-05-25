package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushReservationDto;
import com.onesley.oneclick.shared.events.ReservationCreatedEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Listener Spring Modulith : transforme les events du cycle de vie réservation
 * en notifications côté SERVEUR (cloche in-app via {@link NotificationService} +
 * push FCM best-effort via {@link FcmPushService}).
 *
 * <p>Remplace les anciens appels front {@code POST /api/notifications/push/reservation}
 * (port de l'Edge Function Supabase {@code send-reservation-push}) qui étaient
 * cassés : le front envoyait {@code {event, reservationId}} alors que le DTO exige
 * {@code recipientUserId/title/body}, et le CLIENT n'a pas {@code CREATE:NOTIFICATIONS}.
 * Ici on résout le destinataire ({@code clientId}, porté par l'event) côté serveur,
 * sans secret ni permission côté client.
 *
 * <p>Async + after-commit : {@code @ApplicationModuleListener} = {@code @Async} +
 * {@code @Transactional(REQUIRES_NEW)} + {@code @TransactionalEventListener(AFTER_COMMIT)}.
 *
 * <p>V2 : pour {@code new_reservation}, notifier AUSSI le restaurant (owner/staff)
 * — nécessite de résoudre le propriétaire du resto (API publique du module restaurant),
 * non fait ici pour rester dans la frontière Modulith (event → clientId seulement).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationEventHandler {

    private final NotificationService notificationService;
    private final FcmPushService pushService;

    private static final String DEEP_LINK = "/pocket/oneclick?tab=suivi";

    @ApplicationModuleListener
    public void onReservationCreated(ReservationCreatedEvent event) {
        // Confirmation au client : sa demande est enregistrée (en attente de réponse resto).
        notify(event.clientId(), event.reservationId(), "pending",
            "Réservation envoyée",
            "Votre demande de réservation a bien été envoyée. En attente de confirmation du restaurant.");
    }

    @ApplicationModuleListener
    public void onReservationStatusChanged(ReservationStatusChangedEvent event) {
        String[] tb = titleAndBody(event.newStatus());
        if (tb == null) return; // statuts sans notification client (ex: pending)
        notify(event.clientId(), event.reservationId(), event.newStatus(), tb[0], tb[1]);
    }

    private void notify(UUID recipientUserId, UUID reservationId, String status, String title, String body) {
        if (recipientUserId == null) {
            log.warn("[notif-event] recipient null (resa={}, status={}) — skip", reservationId, status);
            return;
        }
        // 1. Cloche in-app (le livrable réel : la notif persistée que le user voit).
        try {
            notificationService.create(new NotificationCreateDto(
                recipientUserId, "reservation", "inapp", title, body, DEEP_LINK));
        } catch (Exception ex) {
            log.warn("[notif-event] création notif in-app échouée (recipient={}, resa={}): {}",
                recipientUserId, reservationId, ex.getMessage());
        }
        // 2. Push FCM best-effort (stub tant que FCM HTTP v1 n'est pas câblé — B.8.4).
        try {
            pushService.sendReservation(new PushReservationDto(
                reservationId, recipientUserId, status, title, body, DEEP_LINK));
        } catch (Exception ex) {
            log.warn("[notif-event] push FCM échoué (recipient={}, resa={}): {}",
                recipientUserId, reservationId, ex.getMessage());
        }
    }

    /** Libellés client par statut de réservation ; {@code null} = pas de notification. */
    private String[] titleAndBody(String status) {
        return switch (status) {
            case "confirmed"        -> new String[]{"Réservation confirmée", "Votre réservation a été confirmée. À bientôt !"};
            case "refused"          -> new String[]{"Réservation refusée", "Votre réservation n'a pas pu être acceptée."};
            case "counter_proposed" -> new String[]{"Nouvelle proposition", "Le restaurant vous propose un autre créneau pour votre réservation."};
            case "cancelled"        -> new String[]{"Réservation annulée", "Votre réservation a été annulée."};
            case "honored"          -> new String[]{"Merci de votre visite", "Votre réservation a bien été honorée."};
            case "no_show"          -> new String[]{"Réservation non honorée", "Vous avez été marqué·e absent·e à votre réservation."};
            default                 -> null; // pending, ou statut inconnu → pas de notif
        };
    }
}
