package com.onesley.oneclick.core.notification.internal;

import com.onesley.oneclick.core.notification.api.NotificationDtos.NotificationCreateDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushPromoDto;
import com.onesley.oneclick.core.notification.api.NotificationDtos.PushReservationDto;
import com.onesley.oneclick.shared.events.AnnouncementPublishedEvent;
import com.onesley.oneclick.shared.events.ContractExpiringSoonEvent;
import com.onesley.oneclick.shared.events.FamilyMemberAddedEvent;
import com.onesley.oneclick.shared.events.FeedbackCreatedEvent;
import com.onesley.oneclick.shared.events.FeedbackRepliedEvent;
import com.onesley.oneclick.shared.events.FriendshipRequestedEvent;
import com.onesley.oneclick.shared.events.FriendshipRespondedEvent;
import com.onesley.oneclick.shared.events.MemberPostCommentedEvent;
import com.onesley.oneclick.shared.events.MemberPostLikedEvent;
import com.onesley.oneclick.shared.events.NoShowDisputeCreatedEvent;
import com.onesley.oneclick.shared.events.NoShowDisputeResolvedEvent;
import com.onesley.oneclick.shared.events.NoShowPenaltyFinalizedEvent;
import com.onesley.oneclick.shared.events.OfferCreatedEvent;
import com.onesley.oneclick.shared.events.OfferExpiredEvent;
import com.onesley.oneclick.shared.events.PromoRequestReviewedEvent;
import com.onesley.oneclick.shared.events.ReferralActivatedEvent;
import com.onesley.oneclick.shared.events.TierReachedEvent;
import com.onesley.oneclick.shared.events.PointsExpiringSoonEvent;
import com.onesley.oneclick.shared.events.LoyaltyEarnedEvent;
import com.onesley.oneclick.shared.events.PointsGiftedEvent;
import com.onesley.oneclick.shared.events.RedemptionOtpRequestedEvent;
import com.onesley.oneclick.shared.events.ReservationCreatedEvent;
import com.onesley.oneclick.shared.events.RestaurantReferralActivatedEvent;
import com.onesley.oneclick.shared.events.RestaurantRestitutionPaidEvent;
import com.onesley.oneclick.shared.events.ReservationGuestAddedEvent;
import com.onesley.oneclick.shared.events.ReservationGuestRemovedEvent;
import com.onesley.oneclick.shared.events.ReservationGuestRespondedEvent;
import com.onesley.oneclick.shared.events.ReservationReminderDueEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
import com.onesley.oneclick.shared.events.ResourceBookingCreatedEvent;
import com.onesley.oneclick.shared.events.ResourceBookingReminderDueEvent;
import com.onesley.oneclick.shared.events.ResourceBookingStatusChangedEvent;
import com.onesley.oneclick.shared.events.SeminarRequestedEvent;
import com.onesley.oneclick.shared.events.SeminarStatusChangedEvent;
import com.onesley.oneclick.shared.events.StoreOnboardingRequestedEvent;
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
    /** P1pref — filtre des préférences staff (même module core.notification → appel direct). */
    private final StaffNotificationPreferenceService staffPrefs;

    // ─── Catégories de préférence staff (toggles V85) — voir notifyStaff/isStaffCategoryEnabled. ──
    /** Réservation restaurant (nouvelle demande, annulation client) + contestation no-show. */
    private static final String CAT_RESERVATION = "reservation";
    /** Réservation d'activité club PCC (booking ressource) + demande de séminaire. */
    private static final String CAT_BOOKING = "booking";
    /** Comm B2B descendante : annonce tenant. */
    private static final String CAT_SYSTEM = "system";
    /** Restitution de points/CA versée au resto (B6). */
    private static final String CAT_LOYALTY = "loyalty";

    private static final String DEEP_LINK = "/pocket/oneclick?tab=suivi";
    private static final String COMMUNITY_LINK = "/pocket/circle";
    private static final String CIRCLE_POST_LINK_PREFIX = "/pocket/pcc/circle?post=";
    private static final String FAMILY_LINK = "/pocket/pcc/family";
    private static final String FEEDBACK_OWNER_LINK = "/prodesk/pcc-feedbacks";
    private static final String ANNOUNCEMENT_STAFF_LINK = "/prodesk/announcements";
    private static final String SEMINAR_STAFF_LINK = "/prodesk/pcc-seminars";
    private static final String SEMINAR_MEMBER_LINK = "/pocket/pcc/seminaires";
    private static final String VAULT_LINK = "/pocket/vault";
    private static final String CONTRACTS_LINK = "/galaxy/contrats";
    private static final String RESERVATION_STAFF_LINK = "/prodesk/calendrier";
    private static final String PCC_BOOKINGS_STAFF_LINK = "/prodesk/pcc-bookings";
    /** Page staff où atterrit la notif de contestation no-show (le staff gère la résa au calendrier). */
    private static final String DISPUTE_STAFF_LINK = "/prodesk/calendrier";
    private static final String PROMOS_LINK = "/pocket/promos";
    /** Page staff/gérant où atterrit la notif de verdict de modération de demande de promo (B12). */
    private static final String FORGE_PROMOTIONS_LINK = "/forge/promotions";
    /** Page admin où atterrit la notif de parrainage resto→resto (Command Center « Galaxy »). */
    private static final String ADMIN_REFERRAL_LINK = "/galaxy/contrats";
    /** Page admin où atterrit la notif « nouvelle demande d'enseigne » (Command Center « Forge »). */
    private static final String ADMIN_ONBOARDING_LINK = "/forge/demandes-inscription";
    /** Page staff/owner où atterrit la notif « restitution versée » (B6 — Facturation OneClick HI). */
    private static final String RESTITUTION_STAFF_LINK = "/prodesk/oneclick-hi";

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
        pushToUser(event.addresseeId(), "Demande d'ami 👋",
            "Vous avez reçu une nouvelle demande d'ami.", link);   // R4 : push (était in-app only)
    }

    /** Réponse à une demande d'amitié → notif au demandeur (server-side, pas de CREATE:NOTIFICATIONS côté client). */
    @ApplicationModuleListener
    public void onFriendshipResponded(FriendshipRespondedEvent event) {
        String title = event.accepted() ? "Demande acceptée ✅" : "Demande refusée";
        String body = event.accepted()
            ? "Votre demande d'ami a été acceptée !"
            : "Votre demande d'ami a été refusée.";
        createInApp(event.recipientUserId(), "community", title, body, COMMUNITY_LINK);
        pushToUser(event.recipientUserId(), title, body, COMMUNITY_LINK);   // R4 : push (était in-app only)
    }

    /**
     * Commentaire sur un post du mur communautaire (Circle A.2) → notif in-app à l'auteur du post
     * (si ≠ commentateur) + aux membres mentionnés. Destinataires résolus + filtrés côté
     * {@code modules.membercircle} et portés sur l'event (frontière Modulith). Type {@code community}
     * (whitelisté). Port du trigger DB legacy {@code notify_post_commented}.
     */
    @ApplicationModuleListener
    public void onMemberPostCommented(MemberPostCommentedEvent event) {
        String link = CIRCLE_POST_LINK_PREFIX + event.postId();
        String preview = event.contentPreview() == null ? "" : event.contentPreview();
        if (event.postAuthorRecipientId() != null) {
            createInApp(event.postAuthorRecipientId(), "community",
                "💬 " + event.commenterName() + " a commenté ton post", preview, link);
        }
        if (event.mentionedRecipientIds() != null) {
            for (UUID recipient : event.mentionedRecipientIds()) {
                createInApp(recipient, "community",
                    "👋 " + event.commenterName() + " t'a mentionné", preview, link);
            }
        }
    }

    /**
     * Like d'un post (Circle A.2) → notif in-app « ❤️ X a aimé ton post » à l'auteur. L'event n'est
     * publié qu'à la 1re pose du like et jamais en self-like (filtré côté membercircle) → ici on
     * notifie sans condition. Type {@code community}. Port du trigger DB legacy {@code notify_post_liked}.
     */
    @ApplicationModuleListener
    public void onMemberPostLiked(MemberPostLikedEvent event) {
        createInApp(event.postAuthorId(), "community",
            "❤️ " + event.likerName() + " a aimé ton post", "", CIRCLE_POST_LINK_PREFIX + event.postId());
    }

    /**
     * Un membre ajoute un proche à sa liste « Ma Famille » (PCC Lot 5) → notif in-app au proche.
     * Server-side car le CLIENT qui ajoute n'a pas {@code CREATE:NOTIFICATIONS} (un POST front
     * 403'ait). Type {@code community} (relation sociale, valeur whitelistée du CHECK notifications.type).
     */
    /**
     * Demande d'OTP de conversion (Gap #2) → notif in-app au CLIENT porteuse du code.
     * Server-side : le staff n'a pas {@code CREATE:NOTIFICATIONS} et seul le client doit
     * voir le code. Type {@code loyalty} (whitelisté ; le legacy utilisait 'otp' non porté).
     */
    @ApplicationModuleListener
    public void onRedemptionOtpRequested(RedemptionOtpRequestedEvent event) {
        String resto = event.restaurantName() != null ? event.restaurantName() : "le restaurant";
        String body = "Code " + event.code() + " — pour valider la conversion de " + event.points()
            + " pts (" + event.discountDh().stripTrailingZeros().toPlainString() + " MAD) chez " + resto
            + ". Valide 5 minutes.";
        createInApp(event.clientId(), "loyalty", "🔒 Code de confirmation", body, "/pocket/vault");
    }

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
        String body = "Une nouvelle annonce a été publiée pour votre établissement. Touchez pour la lire.";
        // B15a — le legacy poussait aussi du FCM (pas seulement in-app) → in-app + push.
        // P1pref — catégorie « system » (comm B2B descendante). Filtre par toggle staff.
        for (UUID recipient : event.recipientUserIds()) {
            notifyStaff(recipient, CAT_SYSTEM, "announcement", title, body, ANNOUNCEMENT_STAFF_LINK, true);
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
        // P1pref — un séminaire est une demande de réservation d'espace/événement → catégorie booking.
        // type='seminar' (whitelisté V73) conservé pour le routing cloche ; seul le toggle change.
        for (UUID recipient : event.recipientUserIds()) {
            notifyStaff(recipient, CAT_BOOKING, "seminar",
                "Nouvelle demande de séminaire 📩", body, SEMINAR_STAFF_LINK, true);   // R3 : in-app + push staff
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
        pushToUser(event.organizerId(), tb[0], tb[1], SEMINAR_MEMBER_LINK);   // R3 : push membre
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

    /**
     * Invité IDENTIFIÉ RETIRÉ d'une réservation par l'organisateur (Lot B14) → notif in-app
     * « Invitation annulée » à l'invité. Server-side (le CLIENT organisateur n'a pas
     * {@code CREATE:NOTIFICATIONS}). Type {@code reservation} (whitelisté), deep-link suivi résa.
     * <b>In-app SEUL</b> (l'annulation d'une invitation est une info de cohérence, pas une urgence
     * comme l'invitation elle-même). Calque {@link #onReservationGuestAdded}.
     */
    @ApplicationModuleListener
    public void onReservationGuestRemoved(ReservationGuestRemovedEvent event) {
        String who = event.organizerName() != null && !event.organizerName().isBlank()
            ? event.organizerName() : "L'organisateur";
        createInApp(event.invitedUserId(), "reservation", "Invitation annulée",
            who + " a retiré votre invitation à une réservation.", DEEP_LINK);
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
        // Gap #2 — staff du restaurant : nouvelle demande à traiter (in-app + push). Parité legacy
        // (trigger pg_net INSERT → send-reservation-push staff). Destinataires portés sur l'event
        // (résolus côté reservation, frontière Modulith). Lien = agenda staff.
        if (event.staffRecipientIds() != null) {
            String title = "Nouvelle réservation 🔔";
            String body = "Une nouvelle demande de réservation vient d'arriver. Touchez pour la traiter.";
            for (UUID staffId : event.staffRecipientIds()) {
                notifyStaff(staffId, CAT_RESERVATION, "reservation", title, body, RESERVATION_STAFF_LINK, true);
            }
        }
    }

    @ApplicationModuleListener
    public void onReservationStatusChanged(ReservationStatusChangedEvent event) {
        // Gap #1 — l'annulation auto H-2 (demande jamais répondue par le resto) publie
        // newStatus="cancelled" + reason="auto_expired" : message dédié (≠ annulation manuelle).
        String[] tb = "auto_expired".equals(event.reason())
            ? new String[]{"Réservation expirée",
                "Votre demande de réservation n'a pas été confirmée à temps par le restaurant et a été annulée."}
            : titleAndBody(event.newStatus());
        if (tb != null) { // statuts sans notification client (ex: pending) → on saute la part client
            notify(event.clientId(), event.reservationId(), event.newStatus(), tb[0], tb[1]);
        }

        // Lot B9 — annulation initiée par le CLIENT → notifier le staff du resto (in-app + push).
        // Les destinataires (staff actif, client exclu) ne sont portés sur l'event QUE dans ce cas
        // (résolus côté reservation : cancelled + changedBy == client). Pour une annulation par le
        // staff ou système (changedBy null/staff), staffRecipientIds est null → pas de notif staff
        // (anti self-notify). Lien = calendrier staff.
        if ("cancelled".equals(event.newStatus())
                && event.staffRecipientIds() != null && !event.staffRecipientIds().isEmpty()) {
            String staffTitle = "Réservation annulée par le client 🔔";
            String staffBody = "Un client vient d'annuler sa réservation. Touchez pour mettre à jour votre agenda.";
            for (UUID staffId : event.staffRecipientIds()) {
                notifyStaff(staffId, CAT_RESERVATION, "reservation", staffTitle, staffBody, RESERVATION_STAFF_LINK, true);
            }
        }
    }

    /**
     * Rappel de réservation (J-1 / H-2) émis par {@code ReservationCronJobs} → notif in-app
     * <b>avec metadata</b> {@code {reservationId, slot}} (anti-doublon du cron H-2) + push FCM
     * best-effort. Sprint R1 (parité push legacy) : avant, les crons faisaient un pur INSERT
     * sans jamais appeler {@code pushService}.
     */
    @ApplicationModuleListener
    public void onReservationReminderDue(ReservationReminderDueEvent event) {
        try {
            notificationService.createReservationReminder(
                event.recipientUserId(), event.title(), event.body(), event.link(),
                event.reservationId(), event.slot());
        } catch (Exception ex) {
            log.warn("[notif-event] rappel in-app échoué (recipient={}, resa={}, slot={}): {}",
                event.recipientUserId(), event.reservationId(), event.slot(), ex.getMessage());
        }
        try {
            pushService.sendReservation(new PushReservationDto(
                event.reservationId(), event.recipientUserId(), "reminder",
                event.title(), event.body(), event.link()));
        } catch (Exception ex) {
            log.warn("[notif-event] push rappel échoué (recipient={}, resa={}, slot={}): {}",
                event.recipientUserId(), event.reservationId(), event.slot(), ex.getMessage());
        }
    }

    /**
     * Nouveau booking PCC créé (gap #3, parité legacy {@code send-pcc-staff-notification}) → notif
     * in-app + push à chaque staff du tenant. Destinataires résolus côté {@code resource_booking}
     * (requête native) et portés sur l'event (frontière Modulith). Lien = dashboard bookings staff.
     */
    @ApplicationModuleListener
    public void onResourceBookingCreated(ResourceBookingCreatedEvent event) {
        if (event.staffRecipientIds() == null || event.staffRecipientIds().isEmpty()) return;
        String title = "Nouvelle réservation club 🔔";
        String body = "Une nouvelle réservation d'activité vient d'arriver. Touchez pour la traiter.";
        for (UUID staffId : event.staffRecipientIds()) {
            notifyStaff(staffId, CAT_BOOKING, "reservation", title, body, PCC_BOOKINGS_STAFF_LINK, true);
        }
    }

    /**
     * Rappel de booking PCC (J-1 / H-2) émis par {@code ResourceBookingCronJobs} (gap #4) → notif
     * in-app avec metadata {@code {bookingId, slot}} (anti-doublon du cron H-2) + push FCM best-effort.
     * Calque {@link #onReservationReminderDue} (rappels résa restaurant).
     */
    @ApplicationModuleListener
    public void onResourceBookingReminderDue(ResourceBookingReminderDueEvent event) {
        try {
            notificationService.createResourceBookingReminder(
                event.recipientUserId(), event.title(), event.body(), event.link(),
                event.bookingId(), event.slot());
        } catch (Exception ex) {
            log.warn("[notif-event] rappel booking in-app échoué (recipient={}, booking={}, slot={}): {}",
                event.recipientUserId(), event.bookingId(), event.slot(), ex.getMessage());
        }
        pushToUser(event.recipientUserId(), event.title(), event.body(), event.link());
    }

    /**
     * Changement de statut d'un resource_booking PCC (Padel/Spa/Golf/Coiffeur/Palm Gym) → notif
     * in-app + push FCM au client organisateur. R3 (parité legacy {@code notifyPccClient}).
     *
     * <p>{@code confirmed/completed/no_show} sont des issues staff/système → toujours poussées.
     * Pour {@code cancelled}, on route via {@code changedBy} (R3-bis, parité 1:1) : on ne notifie
     * le client que si un <b>tiers</b> a annulé (staff/admin, ou annulation système où
     * {@code changedBy == null}) — jamais quand le membre annule lui-même
     * ({@code changedBy == organizerId}).
     */
    @ApplicationModuleListener
    public void onResourceBookingStatusChanged(ResourceBookingStatusChangedEvent event) {
        boolean memberSelfCancel = "cancelled".equals(event.newStatus())
                && event.changedBy() != null && event.changedBy().equals(event.organizerId());

        if (memberSelfCancel) {
            // Lot B9 — le MEMBRE annule lui-même : pas de push au membre (anti self-notify), mais on
            // notifie le STAFF du tenant (in-app + push) que le créneau s'est libéré. Destinataires
            // portés sur l'event (résolus côté resource_booking, organisateur exclu).
            if (event.staffRecipientIds() != null && !event.staffRecipientIds().isEmpty()) {
                String title = "Réservation club annulée 🔔";
                String body = "Un membre vient d'annuler sa réservation d'activité. Touchez pour mettre à jour l'agenda.";
                for (UUID staffId : event.staffRecipientIds()) {
                    notifyStaff(staffId, CAT_BOOKING, "reservation", title, body, PCC_BOOKINGS_STAFF_LINK, true);
                }
            }
            return; // pas de push au membre lui-même
        }

        String[] tb = bookingStatusTitleBody(event.newStatus());
        if (tb == null) return;
        createInApp(event.organizerId(), "reservation", tb[0], tb[1], DEEP_LINK);
        pushToUser(event.organizerId(), tb[0], tb[1], DEEP_LINK);
    }

    /** Libellés client par statut de booking PCC ; {@code null} = statut sans push client. */
    private String[] bookingStatusTitleBody(String status) {
        return switch (status == null ? "" : status) {
            case "confirmed" -> new String[]{"Réservation confirmée ✅", "Votre activité au club est confirmée. À bientôt !"};
            case "completed" -> new String[]{"Merci de votre visite 🌴", "Votre réservation au club a bien été honorée."};
            case "no_show"   -> new String[]{"Réservation non honorée", "Vous avez été marqué·e absent·e à votre réservation au club."};
            case "cancelled" -> new String[]{"Réservation annulée", "Votre réservation au club a été annulée."};
            default          -> null; // pending → pas de push client
        };
    }

    /**
     * Points fidélité bientôt expirés (J-30/J-15/J-7) émis par {@code LoyaltyCronJobs} → notif in-app
     * <b>avec metadata</b> {@code {kind:'points_expiring', accountId, milestone}} (anti-doublon du cron)
     * + push FCM au client. Feature A (parité legacy {@code notify-expiring-points}, enrichie du push).
     */
    @ApplicationModuleListener
    public void onPointsExpiringSoon(PointsExpiringSoonEvent event) {
        String title = "Vos points expirent bientôt ⏳";
        String body = pointsExpiringBody(event.points(), event.expiresLabel(), event.milestone());
        try {
            notificationService.createPointsExpiringAlert(
                event.recipientUserId(), title, body, VAULT_LINK, event.accountId(), event.milestone());
        } catch (Exception ex) {
            log.warn("[notif-event] alerte points in-app échouée (recipient={}, account={}, milestone={}): {}",
                event.recipientUserId(), event.accountId(), event.milestone(), ex.getMessage());
        }
        pushToUser(event.recipientUserId(), title, body, VAULT_LINK);
    }

    /** Corps de l'alerte points, urgence croissante selon le jalon. */
    private String pointsExpiringBody(int points, String date, String milestone) {
        return switch (milestone == null ? "" : milestone) {
            case "j7"  -> "Dernière semaine ! " + points + " points expirent le " + date
                          + ". Profitez-en avant qu'ils ne soient perdus.";
            case "j15" -> "Plus que 15 jours : " + points + " points expirent le " + date + ".";
            default    -> "Vous avez " + points + " points qui expirent le " + date + ". Pensez à en profiter !";
        };
    }

    /**
     * Contrat partenaire bientôt expiré, sans renouvellement auto (J-30/J-15/J-7) émis par
     * {@code FinancialCronJobs} → notif in-app (type {@code system}, metadata anti-doublon
     * {@code {kind:'contract_expiring', contractId, milestone}}) + push FCM, pour chaque admin.
     * Feature B (parité legacy {@code notify-expiring-contracts}). Destinataires (SUPERADMIN) résolus
     * côté financial et portés sur l'event (frontière Modulith : notification ne dépend pas d'identity).
     */
    @ApplicationModuleListener
    public void onContractExpiringSoon(ContractExpiringSoonEvent event) {
        if (event.recipientAdminIds() == null || event.recipientAdminIds().isEmpty()) return;
        String title = "Contrat à renouveler ⚠️";
        String resto = event.restaurantName() != null ? " (" + event.restaurantName() + ")" : "";
        String number = event.contractNumber() != null ? event.contractNumber() : "sans n°";
        String body = "Le contrat " + number + resto + " expire le " + event.endsLabel()
            + " sans renouvellement automatique. Action requise.";
        for (UUID admin : event.recipientAdminIds()) {
            try {
                notificationService.createContractExpiringAlert(
                    admin, title, body, CONTRACTS_LINK, event.contractId(), event.milestone());
            } catch (Exception ex) {
                log.warn("[notif-event] alerte contrat in-app échouée (admin={}, contract={}, milestone={}): {}",
                    admin, event.contractId(), event.milestone(), ex.getMessage());
            }
            pushToUser(admin, title, body, CONTRACTS_LINK);
        }
    }

    /**
     * Contestation no-show CRÉÉE par le client (Lot B8) → notif in-app + push à chaque staff du
     * restaurant concerné (destinataires résolus côté {@code reservation} et portés sur l'event,
     * client contestataire exclu). Server-side : le CLIENT contestataire n'a pas
     * {@code CREATE:NOTIFICATIONS}. Type {@code reservation} (whitelisté), deep-link calendrier staff
     * (le staff y gère la réservation/no-show). In-app + push, comme une nouvelle réservation.
     */
    @ApplicationModuleListener
    public void onDisputeCreated(NoShowDisputeCreatedEvent event) {
        if (event.staffRecipientIds() == null || event.staffRecipientIds().isEmpty()) return;
        String who = event.clientName() != null && !event.clientName().isBlank()
            ? event.clientName() : "Un·e client·e";
        String title = "Contestation no-show ⚖️";
        String body = who + " conteste une absence (no-show). Touchez pour examiner la réservation.";
        for (UUID staffId : event.staffRecipientIds()) {
            notifyStaff(staffId, CAT_RESERVATION, "reservation", title, body, DISPUTE_STAFF_LINK, true);
        }
    }

    /**
     * Contestation no-show résolue (CH-3 challenge) → notif in-app + push au client contestataire,
     * libellé selon l'issue. L'event est déjà consommé par {@code loyalty} (réversion de la pénalité) ;
     * ce listener ajoute la transparence côté client (acceptée = récup' satisfaction ; refusée = clarté).
     */
    @ApplicationModuleListener
    public void onDisputeResolved(NoShowDisputeResolvedEvent event) {
        if (event.clientId() == null) return;
        String title = event.accepted() ? "Contestation acceptée ✅" : "Contestation refusée";
        String body = event.accepted()
            ? "Votre contestation a été acceptée : la marque de non-présentation et sa pénalité ont été retirées de votre compte."
            : "Votre contestation a été examinée mais refusée : la marque de non-présentation est maintenue.";
        createInApp(event.clientId(), "reservation", title, body, DEEP_LINK);
        pushToUser(event.clientId(), title, body, DEEP_LINK);
    }

    /**
     * Pénalité no-show devenue DÉFINITIVE (48h sans contestation acceptée/pending) → notif in-app de
     * TRANSPARENCE au client (Lot B10). La pénalité de réputation a déjà été appliquée au marquage du
     * no_show ; on n'en applique PAS de nouvelle ici — on informe seulement le client que le délai de
     * contestation (48h) est expiré. {@code metadata = {kind:'noshow_penalty_final', reservationId}}
     * (anti-doublon du cron). Type {@code reservation}, deep-link suivi résa. <b>In-app SEUL</b>.
     */
    @ApplicationModuleListener
    public void onNoShowPenaltyFinalized(NoShowPenaltyFinalizedEvent event) {
        if (event.clientId() == null) return;
        String title = "Pénalité no-show appliquée";
        String body = "Le délai de contestation (48h) de votre absence (no-show) est écoulé. "
            + "La marque de non-présentation et sa pénalité de réputation sont désormais définitives "
            + "et ne peuvent plus être contestées.";
        try {
            notificationService.createNoShowPenaltyFinalAlert(
                event.clientId(), title, body, DEEP_LINK, event.reservationId());
        } catch (Exception ex) {
            log.warn("[notif-event] alerte pénalité no-show in-app échouée (client={}, resa={}): {}",
                event.clientId(), event.reservationId(), ex.getMessage());
        }
    }

    /**
     * Nouvelle offre d'un restaurant suivi (CH-1 challenge) → notif in-app + push aux clients qui ont
     * mis ce resto en favori (résolus côté {@code promotion} si {@code push_notify=true}, portés sur
     * l'event). Type {@code promotion}, deep-link Promos.
     */
    @ApplicationModuleListener
    public void onOfferCreated(OfferCreatedEvent event) {
        if (event.favoriteRecipientIds() == null || event.favoriteRecipientIds().isEmpty()) return;
        String title = "Nouvelle offre 🎉";
        String body = (event.title() != null ? event.title() : "Une nouvelle offre")
            + " — dans un restaurant que vous suivez. Touchez pour en profiter.";
        for (UUID recipient : event.favoriteRecipientIds()) {
            createInApp(recipient, "promotion", title, body, PROMOS_LINK);
            pushToUser(recipient, title, body, PROMOS_LINK);
        }
    }

    /**
     * Demande de push promo modérée (Lot B12) → notif in-app au DEMANDEUR (gérant) du verdict.
     * Server-side : le demandeur n'a pas {@code CREATE:NOTIFICATIONS}. Type {@code promotion}
     * (whitelisté), deep-link vers la page de gestion des promotions. <b>In-app SEUL</b> (info de
     * suivi back-office, pas une alerte client). Si {@code requesterId} null (demande sans demandeur
     * tracé), {@link #createInApp} skip.
     */
    @ApplicationModuleListener
    public void onPromoRequestReviewed(PromoRequestReviewedEvent event) {
        String promo = event.title() != null && !event.title().isBlank()
            ? " « " + truncate(event.title(), 64) + " »" : "";
        if (event.approved()) {
            createInApp(event.requesterId(), "promotion", "Demande de promo approuvée ✅",
                "Votre demande de notification promo" + promo + " a été approuvée et sera diffusée.",
                FORGE_PROMOTIONS_LINK);
        } else {
            String reason = event.rejectionReason() != null && !event.rejectionReason().isBlank()
                ? " Motif : " + truncate(event.rejectionReason(), 200) : "";
            createInApp(event.requesterId(), "promotion", "Demande de promo refusée",
                "Votre demande de notification promo" + promo + " n'a pas été retenue." + reason,
                FORGE_PROMOTIONS_LINK);
        }
    }

    /**
     * Offre/featured EXPIRÉE par le cron (Lot B11) → notif in-app au STAFF du restaurant
     * (destinataires résolus côté {@code modules.promotion} et portés sur l'event, frontière
     * Modulith — calque {@link #onResourceBookingCreated}). Catégorie de préférence {@code system}
     * (comm back-office). Type {@code system} (whitelisté ; pas de canal dédié « offre expirée »),
     * deep-link gestion promotions. <b>In-app SEUL</b> (info de suivi, pas une alerte). Idempotence
     * garantie côté cron (ne publie qu'au passage actif→expiré).
     */
    @ApplicationModuleListener
    public void onOfferExpired(OfferExpiredEvent event) {
        if (event.staffRecipientIds() == null || event.staffRecipientIds().isEmpty()) return;
        String promo = event.title() != null && !event.title().isBlank()
            ? " « " + truncate(event.title(), 64) + " »" : "";
        String title = "Offre expirée ⌛";
        String body = "Votre offre" + promo + " a expiré et n'est plus visible. "
            + "Touchez pour la renouveler ou en créer une nouvelle.";
        for (UUID staffId : event.staffRecipientIds()) {
            notifyStaff(staffId, CAT_SYSTEM, "system", title, body, FORGE_PROMOTIONS_LINK, false);
        }
    }

    /**
     * Montée de palier fidélité (CH-3 challenge) → notif in-app + push « célébration » au client.
     * Émis par {@code LoyaltyService} uniquement au franchissement (jamais sur chaque gain). Type
     * {@code loyalty}, deep-link Vault.
     */
    @ApplicationModuleListener
    public void onTierReached(TierReachedEvent event) {
        if (event.clientId() == null) return;
        String title = "Nouveau palier atteint 🎉";
        String body = "Félicitations ! Vous avez atteint le palier " + event.tierName()
            + " avec " + event.totalPoints() + " points. Profitez de vos avantages.";
        createInApp(event.clientId(), "loyalty", title, body, VAULT_LINK);
        pushToUser(event.clientId(), title, body, VAULT_LINK);
    }

    /**
     * Parrainage RESTAURANT-à-RESTAURANT (owner → owner) activé (Lot B3) → notif in-app à chaque
     * <b>admin plateforme</b> (parité legacy : ligne {@code admin_notifications}). Destinataires
     * (SUPERADMIN) résolus côté {@code restaurant_referral} (qui a la dépendance {@code core.identity})
     * et portés sur l'event — pattern {@link #onContractExpiringSoon}. {@code core.notification} reste
     * sans dépendance identity et n'a qu'à itérer. Type {@code system} (whitelisté ; pas de canal
     * « admin » dédié), deep-link Command Center. <b>In-app SEUL</b> (parité legacy — pas de push admin).
     */
    @ApplicationModuleListener
    public void onRestaurantReferralActivated(RestaurantReferralActivatedEvent event) {
        if (event.recipientAdminIds() == null || event.recipientAdminIds().isEmpty()) return;
        String title = "Nouveau parrainage restaurant 🤝";
        String body = "Un restaurant vient d'en parrainer un autre : "
            + event.rewardPoints() + " points crédités au parrain.";
        for (UUID admin : event.recipientAdminIds()) {
            createInApp(admin, "system", title, body, ADMIN_REFERRAL_LINK);
        }
    }

    /**
     * Nouvelle demande d'inscription d'enseigne soumise par un gérant (Lot B4, port EF legacy
     * {@code send-onboarding-request}) → notif in-app à chaque <b>admin plateforme</b> (parité legacy :
     * ligne {@code admin_notifications}). Destinataires (SUPERADMIN) résolus côté {@code modules.store}
     * (qui a la dépendance {@code core.identity}) et portés sur l'event — pattern
     * {@link #onContractExpiringSoon} / {@link #onRestaurantReferralActivated}. {@code core.notification}
     * reste sans dépendance identity et n'a qu'à itérer. Type {@code system} (whitelisté), deep-link vers
     * la page admin des demandes d'inscription. <b>In-app SEUL</b> (push optionnel non requis).
     */
    @ApplicationModuleListener
    public void onStoreOnboardingRequested(StoreOnboardingRequestedEvent event) {
        if (event.recipientAdminIds() == null || event.recipientAdminIds().isEmpty()) return;
        String resto = event.restaurantName() != null ? event.restaurantName() : "Une enseigne";
        String who = event.contactName() != null && !event.contactName().isBlank()
            ? " (gérant : " + event.contactName() + ")" : "";
        String title = "Nouvelle demande d'enseigne 🏪";
        String body = resto + who + " souhaite rejoindre OneClick. Touchez pour examiner la demande.";
        for (UUID admin : event.recipientAdminIds()) {
            createInApp(admin, "system", title, body, ADMIN_ONBOARDING_LINK);
        }
    }

    /**
     * Restitution de points/CA <b>versée</b> à un restaurant (Lot B6) → notif in-app au <b>staff
     * actif</b> du resto bénéficiaire (destinataires résolus côté {@code modules.loyalty} et portés
     * sur l'event, frontière Modulith — calque {@code ResourceBookingCreatedEvent}). Catégorie de
     * préférence {@code loyalty} (P1pref) — un staff qui a désactivé « loyalty » n'est pas notifié.
     * Type {@code system} (whitelisté ; pas de canal dédié restitution), deep-link Facturation.
     * <b>In-app SEUL</b> (versement financier ≈ info admin, comme contrat/parrainage — pas de push).
     */
    @ApplicationModuleListener
    public void onRestaurantRestitutionPaid(RestaurantRestitutionPaidEvent event) {
        if (event.staffRecipientIds() == null || event.staffRecipientIds().isEmpty()) return;
        String amount = event.amount() != null
            ? event.amount().stripTrailingZeros().toPlainString() + " MAD" : "Un montant";
        String title = "Restitution versée 💸";
        String body = amount + " de restitution vient d'être versé·e à votre établissement. "
            + "Touchez pour consulter le détail.";
        for (UUID staffId : event.staffRecipientIds()) {
            // in-app SEUL (push=false) ; filtre catégorie 'loyalty' appliqué par notifyStaff.
            notifyStaff(staffId, CAT_LOYALTY, "system", title, body, RESTITUTION_STAFF_LINK, false);
        }
    }

    /**
     * Points crédités au CLIENT lors d'un scan Snap2Earn (Lot B2) → notif in-app « points gagnés »
     * (parité legacy : à chaque scan validé, le client voyait une notif in-app). <b>In-app SEUL</b>
     * (parité legacy), type {@code loyalty} (whitelisté), deep-link Vault.
     *
     * <p><b>Filtre conservateur</b> : on ne notifie QUE les vrais gains de scan, identifiés par
     * {@code reason} préfixé {@code "snap2earn|"} (cf. {@code LoyaltyService.snap2earn} qui encode
     * {@code "snap2earn|<ticket_ref>|<photo_url?>"}). On exclut donc explicitement les autres gains qui
     * passent par {@code earnPoints} mais ne sont PAS des scans :
     * <ul>
     *   <li>{@code "RESTAURANT_REFERRAL"} (parrainage resto→resto — a déjà sa notif admin via B3) ;</li>
     *   <li>{@code "gift:from:…"} (don de points — flux dédié) ;</li>
     *   <li>{@code "welcome"} (bonus d'enrollment) ;</li>
     *   <li>tout {@code reason} libre fourni à l'endpoint {@code /loyalty/earn} (crédit technique).</li>
     * </ul>
     * Et les montants {@code points <= 0}. La montée de palier (event {@code TierReachedEvent} séparé,
     * {@link #onTierReached}) reste indépendante — pas de doublon.
     */
    @ApplicationModuleListener
    public void onLoyaltyEarned(LoyaltyEarnedEvent event) {
        if (event.clientId() == null || event.points() <= 0) return;
        if (event.reason() == null || !event.reason().startsWith("snap2earn|")) {
            return; // crédits non-scan (referral/gift/welcome/technique) → pas de notif « points gagnés »
        }
        String title = "Points gagnés 🎉";
        String body = "+" + event.points() + " points viennent d'être crédités sur votre compte fidélité.";
        createInApp(event.clientId(), "loyalty", title, body, VAULT_LINK);
    }

    /**
     * Don de points (gift) → notif in-app au BÉNÉFICIAIRE (Lot B7, décision user OUI V1). Server-side :
     * le donneur n'a pas {@code CREATE:NOTIFICATIONS}. Type {@code loyalty} (whitelisté, c'est un
     * +points), deep-link Vault. <b>In-app SEUL</b> (parité avec les autres notifs +points internes).
     * {@code fromName} best-effort : si null → mention générique « un ami ».
     */
    @ApplicationModuleListener
    public void onPointsGifted(PointsGiftedEvent event) {
        if (event.toUserId() == null || event.points() <= 0) return;
        String from = event.fromName() != null && !event.fromName().isBlank()
            ? event.fromName() : "un ami";
        createInApp(event.toUserId(), "loyalty", "Cadeau de points 🎁",
            "+" + event.points() + " pts offerts par " + from + ".", VAULT_LINK);
    }

    /**
     * Parrainage CLIENT activé (Lot B1, code parrain consommé au signup / activation) → notif in-app
     * au PARRAIN <b>et</b> au FILLEUL (parité legacy). Titres distincts. Type {@code loyalty}
     * (whitelisté, c'est un +points), deep-link Vault. <b>In-app SEUL</b> (parité legacy — pas de
     * push). Si l'un des deux ids est {@code null}, {@link #createInApp} skip ce destinataire (garde).
     * Distinct de {@link #onRestaurantReferralActivated} (parrainage resto→resto, notif admin).
     */
    @ApplicationModuleListener
    public void onReferralActivated(ReferralActivatedEvent event) {
        int pts = event.rewardPoints();
        createInApp(event.referrerUserId(), "loyalty",
            "Parrainage réussi 🎉 +" + pts + " pts",
            "Votre filleul·e a rejoint OneClick grâce à votre code. " + pts + " points vous sont offerts.",
            VAULT_LINK);
        createInApp(event.referredUserId(), "loyalty",
            "Bienvenue ! +" + pts + " pts offerts",
            "Vous avez rejoint OneClick via un parrainage. " + pts + " points de bienvenue vous sont offerts.",
            VAULT_LINK);
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

    /**
     * Push FCM best-effort vers un destinataire unique (canal communauté/divers) — réutilise
     * {@code sendPromo} (fan-out par {@code userIds}) pour un seul user. R4 : sortie de l'in-app-only
     * des demandes d'ami (parité legacy {@code send-friend-request-push}).
     */
    private void pushToUser(UUID recipientUserId, String title, String body, String link) {
        if (recipientUserId == null) return;
        try {
            pushService.sendPromo(new PushPromoDto(null, java.util.List.of(recipientUserId), title, body, link));
        } catch (Exception ex) {
            log.warn("[notif-event] push direct échoué (recipient={}): {}", recipientUserId, ex.getMessage());
        }
    }

    /**
     * P1pref — notifie un STAFF (in-app + push) <b>en respectant sa préférence de catégorie</b>
     * (toggles V85). Si le toggle {@code category} de {@code staffId} est OFF → on saute ENTIÈREMENT
     * ce destinataire (ni in-app ni push). Défaut ON si le staff n'a aucune préférence enregistrée
     * (cf. {@link StaffNotificationPreferenceService#isStaffCategoryEnabled}).
     *
     * <p>N'affecte QUE les notifications staff : les notifs CLIENT/MEMBRE et ADMIN continuent
     * d'utiliser {@link #createInApp}/{@link #pushToUser} directement, sans filtre.
     *
     * @param category l'une des 5 catégories ({@code booking}/{@code reservation}/{@code feedback}/
     *                 {@code loyalty}/{@code system}) — détermine le toggle consulté
     * @param type     type de la notif persistée (valeur whitelistée du CHECK {@code notifications.type})
     * @param push     true → push FCM en plus de l'in-app ; false → in-app seule
     */
    private void notifyStaff(UUID staffId, String category, String type,
                             String title, String body, String link, boolean push) {
        if (staffId == null) return;
        if (!staffPrefs.isStaffCategoryEnabled(staffId, category)) {
            log.debug("[notif-event] staff {} a désactivé la catégorie '{}' — notif '{}' ignorée",
                staffId, category, type);
            return; // toggle OFF → skip in-app ET push pour ce destinataire
        }
        createInApp(staffId, type, title, body, link);
        if (push) {
            pushToUser(staffId, title, body, link);
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
        // 2. Push FCM best-effort (FCM HTTP v1 réel — B.8.4 ; mode stub si app.fcm.* absents).
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
