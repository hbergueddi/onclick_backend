package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.shared.events.NoShowPenaltyFinalizedEvent;
import com.onesley.oneclick.shared.events.ReservationReminderDueEvent;
import com.onesley.oneclick.shared.events.ReservationStatusChangedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Crons du module reservation — Sprint G.3 (port pg_cron legacy).
 *
 * <p>Schedules :
 * <ul>
 *   <li>{@code expire-unanswered-reservations} : H-2 avant la résa, passe à
 *     cancelled les {@code pending} non répondues par le staff ET les
 *     {@code counter_proposed} (V89) dont le créneau proposé n'a pas été accepté
 *     par le client (toutes les 15 min)</li>
 *   <li>{@code send-reservation-reminders-j1} : J-1 à 9h Maroc, rappelle aux
 *     clients confirmés la résa du lendemain (1 fois par jour)</li>
 *   <li>{@code send-reservation-reminders-h2} : H-2 avant la résa, dernier
 *     rappel (toutes les 15 min)</li>
 * </ul>
 *
 * <p><b>Sprint R1 (parité push legacy)</b> : les deux rappels ne font plus un pur
 * {@code INSERT} dans {@code notifications} (l'ancien {@code channel='push'} était
 * trompeur — 0 FCM réel). Ils sélectionnent les réservations dues et publient un
 * {@link ReservationReminderDueEvent} par destinataire ; {@code NotificationEventHandler}
 * crée alors la notif in-app (avec {@code metadata} pour l'anti-doublon H-2) <b>et</b>
 * déclenche le push FCM via {@code FcmPushService} (frontière Modulith respectée :
 * le module reservation ne dépend pas de core.notification).
 *
 * <p>Pattern : log INFO start/done + count des rows. Permet de tracer via Sentry/Datadog
 * l'exécution des jobs (alerting si 0 rows pendant 24h sur un job qui devrait tourner).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReservationCronJobs {

    private static final String REMINDER_LINK = "/pocket/oneclick?tab=suivi";

    @PersistenceContext
    private EntityManager em;

    private final ApplicationEventPublisher events;

    /**
     * Annule les réservations non répondues 2h avant le RDV, sur deux fronts :
     * <ul>
     *   <li>{@code pending} : le <b>staff</b> n'a pas répondu → on compare au
     *       créneau demandé {@code reservation_at}.</li>
     *   <li>{@code counter_proposed} : le <b>client</b> n'a pas accepté la
     *       contre-proposition (V89) → on compare au créneau <b>proposé</b>
     *       {@code proposed_reservation_at} (le créneau qui deviendrait effectif).
     *       Fallback {@code COALESCE(proposed_reservation_at, reservation_at)} : si le
     *       créneau proposé est NULL (anomalie — ex. seed créé par INSERT direct qui
     *       court-circuite la validation service), on retombe sur le créneau demandé
     *       d'origine, pour ne pas laisser la résa coincée indéfiniment en « En attente ».</li>
     * </ul>
     * Toutes les 15 min, à 0/15/30/45 (aligné pg_cron legacy).
     */
    @Scheduled(cron = "0 0/15 * * * *")
    @Transactional
    public void expireUnansweredReservations() {
        log.info("[cron] expireUnansweredReservations starting...");
        // Gap #1 — avant : pur UPDATE bulk → le client n'était JAMAIS notifié de
        // l'annulation auto. Maintenant : on SELECT les dues (id + contexte), on UPDATE,
        // puis on publie un ReservationStatusChangedEvent(reason="auto_expired") par ligne
        // → NotificationEventHandler crée la notif in-app + push client (message dédié).
        // NB : la table `reservations` n'a ni `cancelled_at` ni `cancellation_reason`
        // (greenfield Spring) ; on met `updated_at` à jour car l'UPDATE natif court-circuite
        // le listener JPA @LastModifiedDate.
        @SuppressWarnings("unchecked")
        List<Object[]> due = em.createNativeQuery("""
                SELECT r.id, r.client_id, r.restaurant_id, r.tenant_id, r.status
                FROM reservations r
                WHERE r.deleted_at IS NULL
                  AND (
                        (r.status = 'pending'
                             AND r.reservation_at <= NOW() + INTERVAL '2 hours')
                     OR (r.status = 'counter_proposed'
                             AND COALESCE(r.proposed_reservation_at, r.reservation_at)
                                     <= NOW() + INTERVAL '2 hours')
                  )
                """).getResultList();
        if (due.isEmpty()) {
            log.info("[cron] expireUnansweredReservations done: 0 reservations expirées");
            return;
        }
        List<UUID> ids = due.stream().map(row -> toUuid(row[0])).toList();
        int updated = em.createNativeQuery("""
                UPDATE reservations
                SET status = 'cancelled', updated_at = NOW()
                WHERE id IN (:ids)
                """).setParameter("ids", ids).executeUpdate();
        for (Object[] row : due) {
            events.publishEvent(new ReservationStatusChangedEvent(
                toUuid(row[0]), toUuid(row[1]), toUuid(row[2]), toUuid(row[3]),
                String.valueOf(row[4]), "cancelled", "auto_expired", Instant.now()));
        }
        log.info("[cron] expireUnansweredReservations done: {} reservations expirées", updated);
    }

    /**
     * Auto-marque {@code no_show} les réservations <b>confirmées</b> dont l'heure est passée
     * depuis plus de 2h et que le staff n'a jamais clôturées (ni {@code honored} ni
     * {@code no_show}). Décision produit (2026-06-20) : une confirmée laissée en plan après
     * l'heure est présumée non honorée ; le client peut ensuite la <b>contester</b> (fenêtre
     * 48h, cf {@link NoShowDisputeService}).
     *
     * <p>Seuil : {@code reservation_at <= NOW() - INTERVAL '2 hours'} (grâce de 2h — laisse
     * finir le service et au staff le temps de marquer {@code honored}). <b>Pas de borne
     * basse</b> : au 1er run, tout le backlog de confirmées passées est balayé (décision
     * produit explicite).
     *
     * <p>Effets en cascade (déjà câblés via l'event — frontière Modulith respectée) :
     * <ul>
     *   <li>{@code loyalty.ReservationRatingListener} → pénalité réputation {@code -penaliteNoShow} (0.5) ;</li>
     *   <li>{@code core.notification.NotificationEventHandler} → notif in-app + push client
     *       (« Réservation non honorée ») ;</li>
     *   <li>{@code no_show_marked_at = NOW()} ouvre la fenêtre de contestation 48h.</li>
     * </ul>
     * On pose {@code late_cancellation = false} : c'est un no-show <b>système contestable</b>,
     * pas une annulation tardive. Toutes les 15 min, aligné sur l'auto-cancel.
     */
    @Scheduled(cron = "0 0/15 * * * *")
    @Transactional
    public void autoMarkNoShowPastConfirmed() {
        log.info("[cron] autoMarkNoShowPastConfirmed starting (confirmées passées +2h → no_show)...");
        @SuppressWarnings("unchecked")
        List<Object[]> due = em.createNativeQuery("""
                SELECT r.id, r.client_id, r.restaurant_id, r.tenant_id
                FROM reservations r
                WHERE r.deleted_at IS NULL
                  AND r.status = 'confirmed'
                  AND r.reservation_at <= NOW() - INTERVAL '2 hours'
                """).getResultList();
        if (due.isEmpty()) {
            log.info("[cron] autoMarkNoShowPastConfirmed done: 0 confirmées passées");
            return;
        }
        List<UUID> ids = due.stream().map(row -> toUuid(row[0])).toList();
        int updated = em.createNativeQuery("""
                UPDATE reservations
                SET status = 'no_show',
                    no_show_marked_at = NOW(),
                    late_cancellation = false,
                    updated_at = NOW()
                WHERE id IN (:ids)
                """).setParameter("ids", ids).executeUpdate();
        for (Object[] row : due) {
            events.publishEvent(new ReservationStatusChangedEvent(
                toUuid(row[0]), toUuid(row[1]), toUuid(row[2]), toUuid(row[3]),
                "confirmed", "no_show", "auto_no_show", Instant.now()));
        }
        log.info("[cron] autoMarkNoShowPastConfirmed done: {} confirmées → no_show", updated);
    }

    /**
     * Rappel J-1 à 8h heure Maroc (zone Africa/Casablanca) — pour les résas confirmées du lendemain.
     * Publie un {@link ReservationReminderDueEvent} par client → notif in-app + push FCM.
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Casablanca")
    @Transactional
    public void sendReservationRemindersJ1() {
        log.info("[cron] sendReservationRemindersJ1 starting (J-1, 8h Maroc)...");
        @SuppressWarnings("unchecked")
        List<Object[]> due = em.createNativeQuery("""
                SELECT r.client_id, r.id,
                       to_char(r.reservation_at AT TIME ZONE 'Africa/Casablanca', 'HH24:MI') AS hhmm
                FROM reservations r
                WHERE r.status = 'confirmed'
                  AND r.deleted_at IS NULL
                  AND r.reservation_at::date = (CURRENT_DATE + INTERVAL '1 day')::date
                """).getResultList();
        for (Object[] row : due) {
            UUID clientId = toUuid(row[0]);
            UUID reservationId = toUuid(row[1]);
            String hhmm = String.valueOf(row[2]);
            events.publishEvent(new ReservationReminderDueEvent(
                clientId, reservationId, "j1",
                "Demain à " + hhmm,
                "Votre réservation est demain. Pensez à venir à l'heure !",
                REMINDER_LINK, Instant.now()));
        }
        log.info("[cron] sendReservationRemindersJ1 done: {} reminders published", due.size());
    }

    /**
     * Rappel H-2 — toutes les 15 min, pour les résas confirmées dans ~2h.
     * Anti-doublon : la notif déjà créée pour ce slot (metadata {@code slot='h2'},
     * créée par le listener à la course précédente) exclut la réservation du SELECT.
     */
    @Scheduled(cron = "0 0/15 * * * *")
    @Transactional
    public void sendReservationRemindersH2() {
        log.info("[cron] sendReservationRemindersH2 starting (H-2)...");
        @SuppressWarnings("unchecked")
        List<Object[]> due = em.createNativeQuery("""
                SELECT r.client_id, r.id
                FROM reservations r
                WHERE r.status = 'confirmed'
                  AND r.deleted_at IS NULL
                  AND r.reservation_at BETWEEN NOW() + INTERVAL '1 hour 45 minutes'
                                           AND NOW() + INTERVAL '2 hours 15 minutes'
                  AND NOT EXISTS (
                      SELECT 1 FROM notifications n
                      WHERE n.recipient_user_id = r.client_id
                        AND n.created_at > NOW() - INTERVAL '3 hours'
                        AND n.metadata->>'reservationId' = r.id::text
                        AND n.metadata->>'slot' = 'h2'
                  )
                """).getResultList();
        for (Object[] row : due) {
            UUID clientId = toUuid(row[0]);
            UUID reservationId = toUuid(row[1]);
            events.publishEvent(new ReservationReminderDueEvent(
                clientId, reservationId, "h2",
                "Réservation dans 2h",
                "À tout à l'heure ! Pensez à venir à l'heure.",
                REMINDER_LINK, Instant.now()));
        }
        log.info("[cron] sendReservationRemindersH2 done: {} reminders published", due.size());
    }

    /**
     * Lot B10 — TRANSPARENCE : notifie le client quand la fenêtre de contestation 48h d'un no_show
     * expire sans contestation pending/accepted. La pénalité de réputation a déjà été appliquée au
     * <b>marquage</b> du no_show ({@code loyalty.ReservationRatingListener}, status→no_show) ; ce cron
     * ne la ré-applique PAS — il publie un {@link NoShowPenaltyFinalizedEvent} → notif in-app de
     * transparence (« délai de contestation expiré, pénalité définitive »).
     *
     * <p>Critères du SELECT (toute l'idempotence + l'éligibilité y sont, comme le rappel H-2) :
     * <ul>
     *   <li>{@code status = 'no_show'} avec {@code no_show_marked_at} daté de +48h ;</li>
     *   <li>{@code late_cancellation = false} (annulation tardive = non contestable, pas de
     *       transparence « contestation expirée » à donner) ;</li>
     *   <li>aucune contestation {@code pending}/{@code accepted} sur cette résa (sinon ce n'est pas
     *       « non contestée » ; une contestation acceptée a d'ailleurs reversé la pénalité) ;</li>
     *   <li>aucune notif {@code noshow_penalty_final} déjà émise pour cette résa (anti-doublon par
     *       metadata — pas de migration, calque l'anti-doublon H-2).</li>
     * </ul>
     * Toutes les heures (la fenêtre 48h n'a pas besoin de la granularité 15 min des rappels).
     */
    @Scheduled(cron = "0 30 * * * *")
    @Transactional
    public void finalizeNoShowPenalties() {
        log.info("[cron] finalizeNoShowPenalties starting (no-show +48h non contesté)...");
        @SuppressWarnings("unchecked")
        List<Object[]> due = em.createNativeQuery("""
                SELECT r.client_id, r.id
                FROM reservations r
                WHERE r.deleted_at IS NULL
                  AND r.status = 'no_show'
                  AND r.late_cancellation = false
                  AND r.no_show_marked_at IS NOT NULL
                  AND r.no_show_marked_at <= NOW() - INTERVAL '48 hours'
                  AND NOT EXISTS (
                      SELECT 1 FROM no_show_disputes d
                      WHERE d.reservation_id = r.id
                        AND d.status IN ('pending', 'accepted')
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM notifications n
                      WHERE n.recipient_user_id = r.client_id
                        AND n.metadata->>'kind' = 'noshow_penalty_final'
                        AND n.metadata->>'reservationId' = r.id::text
                  )
                """).getResultList();
        for (Object[] row : due) {
            events.publishEvent(new NoShowPenaltyFinalizedEvent(
                toUuid(row[0]), toUuid(row[1]), Instant.now()));
        }
        log.info("[cron] finalizeNoShowPenalties done: {} pénalités no-show finalisées", due.size());
    }

    /** Colonne {@code uuid} d'une requête native : le driver pg renvoie un {@link UUID}, fallback parse défensif. */
    private static UUID toUuid(Object o) {
        return o instanceof UUID u ? u : UUID.fromString(String.valueOf(o));
    }
}
