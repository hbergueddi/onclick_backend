package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushReservationDto;
import com.onesley.oneclick.shared.events.AnnouncementPublishedEvent;
import com.onesley.oneclick.shared.events.FamilyMemberAddedEvent;
import com.onesley.oneclick.shared.events.FeedbackCreatedEvent;
import com.onesley.oneclick.shared.events.FeedbackRepliedEvent;
import com.onesley.oneclick.shared.events.FriendshipRequestedEvent;
import com.onesley.oneclick.shared.events.FriendshipRespondedEvent;
import com.onesley.oneclick.shared.events.ReservationCreatedEvent;
import com.onesley.oneclick.shared.events.ReservationGuestAddedEvent;
import com.onesley.oneclick.shared.events.ReservationGuestRespondedEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
import com.onesley.oneclick.shared.events.SeminarRequestedEvent;
import com.onesley.oneclick.shared.events.SeminarStatusChangedEvent;
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
    private static final String COMMUNITY_LINK = "/pocket/circle";
    private static final String FAMILY_LINK = "/pocket/pcc/family";
    private static final String FEEDBACK_OWNER_LINK = "/prodesk/pcc-feedbacks";
    private static final String ANNOUNCEMENT_STAFF_LINK = "/prodesk/announcements";
    private static final String SEMINAR_STAFF_LINK = "/prodesk/pcc-seminars";
    private static final String SEMINAR_MEMBER_LINK = "/pocket/pcc/seminaires";

    /**
     * Demande d'amitié → notif in-app au destinataire. Server-side car le CLIENT
     * demandeur n'a pas {@code CREATE:NOTIFICATIONS} (le POST front 403'ait).
     */
    @ApplicationModuleListener
    public void onFriendshipRequested(FriendshipRequestedEvent event) {
        // type=friend_request + link=friendshipId : la cloche (GlassHeader) reconnaît
        // ce type, ouvre la pop-up d'acceptation et appelle acceptFriendship(link).
        // Le type 'friend_request' est whitelisté dans le CHECK notifications.type (V39).
        String link = event.friendshipId() != null ? event.friendshipId().toString() : COMMUNITY_LINK;
        createInApp(event.addresseeId(), "friend_request", "Demande d'ami 👋",
            "Vous avez reçu une nouvelle demande d'ami.", link);
    }

    /** Réponse à une demande d'amitié → notif au demandeur (server-side, pas de CREATE:NOTIFICATIONS côté client). */
    @ApplicationModuleListener
    public void onFriendshipResponded(FriendshipRespondedEvent event) {
        String title = event.accepted() ? "Demande acceptée ✅" : "Demande refusée";
        String body = event.accepted()
            ? "Votre demande d'ami a été acceptée !"
            : "Votre demande d'ami a été refusée.";
        createInApp(event.recipientUserId(), "community", title, body, COMMUNITY_LINK);
    }

    /**
     * Un membre ajoute un proche à sa liste « Ma Famille » (PCC Lot 5) → notif in-app au proche.
     * Server-side car le CLIENT qui ajoute n'a pas {@code CREATE:NOTIFICATIONS} (un POST front
     * 403'ait). Type {@code community} (relation sociale, valeur whitelistée du CHECK notifications.type).
     */
    @ApplicationModuleListener
    public void onFamilyMemberAdded(FamilyMemberAddedEvent event) {
        createInApp(event.relatedMemberId(), "community", "Ajouté à une famille 👨‍👩‍👧",
            "Un membre vous a ajouté à sa liste famille. Vous pouvez retirer ce lien depuis votre profil.",
            FAMILY_LINK);
    }

    /**
     * Avis membre (PCC Lot 7) → notif in-app aux destinataires résolus côté feedback
     * (owners du resto ciblé + tenant-admins), auteur exclu. Server-side (le CLIENT qui
     * envoie n'a pas {@code CREATE:NOTIFICATIONS}). Type {@code community} (whitelisté).
     */
    @ApplicationModuleListener
    public void onFeedbackCreated(FeedbackCreatedEvent event) {
        if (event.recipientUserIds() == null || event.recipientUserIds().isEmpty()) return;
        String title = "happy".equals(event.sentiment()) ? "Nouvel avis positif 💚" : "Nouvel avis à traiter 🟠";
        String body = "Un membre a laissé un avis" + (event.category() != null ? " (" + event.category() + ")." : ".");
        for (UUID recipient : event.recipientUserIds()) {
            createInApp(recipient, "community", title, body, FEEDBACK_OWNER_LINK);
        }
    }

    /**
     * Réponse d'Adil (owner/admin) à un avis (PCC Lot 7) → notif in-app au membre auteur,
     * deep-link vers le thread. Server-side (cohérent avec les autres notifs relationnelles).
     */
    @ApplicationModuleListener
    public void onFeedbackReplied(FeedbackRepliedEvent event) {
        String link = "/pocket/pcc/feedback?thread=" + event.feedbackId();
        createInApp(event.memberId(), "community", "Réponse à votre avis ✍️",
            "Adil a répondu à votre avis. Touchez pour lire la réponse.", link);
    }

    /**
     * Annonce tenant publiée (Lot 8) → notif in-app au staff actif du tenant (destinataires résolus
     * côté {@code modules.announcement} et portés sur l'event, auteur exclu). Server-side : c'est une
     * comm B2B descendante, le staff ne s'auto-notifie pas. Type {@code announcement} (whitelisté
     * dans le CHECK {@code notifications.type}). Deep-link vers la page annonces du staff.
     */
    @ApplicationModuleListener
    public void onAnnouncementPublished(AnnouncementPublishedEvent event) {
        if (event.recipientUserIds() == null || event.recipientUserIds().isEmpty()) return;
        String prefix = "urgent".equals(event.priority()) ? "🔴 Annonce urgente" : "📢 Nouvelle annonce";
        String title = prefix + (event.title() != null ? " : " + truncate(event.title(), 80) : "");
        for (UUID recipient : event.recipientUserIds()) {
            createInApp(recipient, "announcement", title,
                "Une nouvelle annonce a été publiée pour votre établissement. Touchez pour la lire.",
                ANNOUNCEMENT_STAFF_LINK);
        }
    }

    /**
     * Nouvelle demande de séminaire (PCC) → notif in-app au staff/admin (« commercial ») du tenant,
     * destinataires résolus côté {@code modules.seminar} et portés sur l'event (auteur exclu).
     * Server-side (le CLIENT qui soumet n'a pas {@code CREATE:NOTIFICATIONS}). Type {@code seminar}
     * (whitelisté V73). Deep-link vers l'inbox commercial.
     */
    @ApplicationModuleListener
    public void onSeminarRequested(SeminarRequestedEvent event) {
        if (event.recipientUserIds() == null || event.recipientUserIds().isEmpty()) return;
        String company = event.companyName() != null ? event.companyName() : "une entreprise";
        String body = "Demande de devis de " + company + ". Touchez pour la traiter.";
        for (UUID recipient : event.recipientUserIds()) {
            createInApp(recipient, "seminar", "Nouvelle demande de séminaire 📩", body, SEMINAR_STAFF_LINK);
        }
    }

    /**
     * Changement de statut d'une demande de séminaire → notif in-app à l'organisateur (membre),
     * libellé dépendant du statut (port des templates de l'EF legacy
     * {@code send-pcc-seminar-status-update}). Server-side. Type {@code seminar} (whitelisté V73).
     * Si {@code organizerId} est null (demande sans compte rattaché), {@link #createInApp} skip.
     */
    @ApplicationModuleListener
    public void onSeminarStatusChanged(SeminarStatusChangedEvent event) {
        String[] tb = seminarStatusMessage(event.newStatus(), event.companyName());
        if (tb == null) return; // statut inconnu → pas de notif
        createInApp(event.organizerId(), "seminar", tb[0], tb[1], SEMINAR_MEMBER_LINK);
    }

    /** Libellés membre par statut de séminaire ; {@code null} = statut inconnu (pas de notif). */
    private String[] seminarStatusMessage(String status, String companyName) {
        String c = companyName != null ? companyName : "votre demande";
        return switch (status == null ? "" : status) {
            case "demandee"      -> new String[]{"Demande reçue — " + c, "Notre équipe commerciale a bien reçu votre demande."};
            case "en_traitement" -> new String[]{"Demande prise en charge — " + c, "Votre demande est en cours d'étude. Devis sous 24h."};
            case "devis_envoye"  -> new String[]{"Devis envoyé — " + c, "Le devis vient de vous être envoyé par email. Vérifiez votre boîte mail."};
            case "confirmee"     -> new String[]{"Séminaire confirmé — " + c, "Votre séminaire est confirmé. À très bientôt !"};
            case "refusee"       -> new String[]{"Demande déclinée — " + c, "Nous ne pouvons malheureusement pas accueillir votre événement aux dates demandées. Notre équipe vous contactera pour une alternative."};
            case "annulee"       -> new String[]{"Demande annulée — " + c, "Votre demande de séminaire a été annulée."};
            default              -> null;
        };
    }

    /**
     * Invité IDENTIFIÉ ajouté à une réservation → notif « Invitation à dîner » +
     * push best-effort (canal réservation). Guests anonymes non concernés (pas d'event).
     */
    @ApplicationModuleListener
    public void onReservationGuestAdded(ReservationGuestAddedEvent event) {
        notify(event.guestUserId(), event.reservationId(), "invited",
            "Invitation à dîner 🍽️",
            "Vous êtes invité·e à une réservation. Consultez vos invitations.");
    }

    /** Un invité répond (accepte/décline) → notif à l'organisateur (server-side). */
    @ApplicationModuleListener
    public void onReservationGuestResponded(ReservationGuestRespondedEvent event) {
        String title = event.accepted() ? "Invitation acceptée ✅" : "Invitation déclinée";
        String body = event.accepted()
            ? "Un invité a accepté votre invitation."
            : "Un invité s'est désisté de votre réservation.";
        notify(event.organizerId(), event.reservationId(),
            event.accepted() ? "guest_accepted" : "guest_declined", title, body);
    }

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

    /** Tronque un libellé à {@code max} caractères (garde-fou pour le title notif, borné à 128). */
    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    /** Notif in-app seule (sans push) — pour les events non-réservation (ex: communauté). */
    private void createInApp(UUID recipientUserId, String type, String title, String body, String link) {
        if (recipientUserId == null) {
            log.warn("[notif-event] recipient null (type={}) — skip", type);
            return;
        }
        try {
            notificationService.create(new NotificationCreateDto(recipientUserId, type, "inapp", title, body, link));
        } catch (Exception ex) {
            log.warn("[notif-event] création notif in-app échouée (recipient={}, type={}): {}",
                recipientUserId, type, ex.getMessage());
        }
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
