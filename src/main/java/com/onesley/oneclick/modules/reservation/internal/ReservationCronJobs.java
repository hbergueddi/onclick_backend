package com.onesley.oneclick.modules.reservation.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Crons du module reservation — Sprint G.3 (port pg_cron legacy).
 *
 * <p>Schedules :
 * <ul>
 *   <li>{@code expire-unanswered-reservations} : H-2 avant la résa, passe les
 *     pending → cancelled si pas de réponse staff (toutes les 15 min)</li>
 *   <li>{@code send-reservation-reminders-j1} : J-1 à 9h Maroc, notifie les
 *     clients confirmés de la résa du lendemain (1 fois par jour)</li>
 *   <li>{@code send-reservation-reminders-h2} : H-2 avant la résa, dernier
 *     rappel (toutes les 15 min)</li>
 * </ul>
 *
 * <p>Pattern : log INFO start/done + count des rows affectés. Permet de tracer
 * via Sentry/Datadog l'exécution des jobs (et alerting si 0 rows pendant 24h
 * sur un job qui devrait tourner).
 */
@Component
@Slf4j
public class ReservationCronJobs {

    @PersistenceContext
    private EntityManager em;

    /**
     * Annule les réservations pending non répondues 2h avant le RDV.
     * Toutes les 15 min, à 0/15/30/45 (aligné pg_cron legacy).
     */
    @Scheduled(cron = "0 0/15 * * * *")
    @Transactional
    public void expireUnansweredReservations() {
        log.info("[cron] expireUnansweredReservations starting...");
        // NB : la table `reservations` n'a ni `cancelled_at` ni `cancellation_reason`
        // (schéma greenfield Spring — seul `status` trace l'annulation). On met
        // `updated_at` à jour explicitement car l'UPDATE natif court-circuite le
        // listener JPA @LastModifiedDate.
        int updated = em.createNativeQuery("""
                UPDATE reservations
                SET status = 'cancelled',
                    updated_at = NOW()
                WHERE status = 'pending'
                  AND deleted_at IS NULL
                  AND reservation_at <= NOW() + INTERVAL '2 hours'
                """).executeUpdate();
        log.info("[cron] expireUnansweredReservations done: {} reservations expirées", updated);
    }

    /**
     * Rappel J-1 à 9h heure Maroc (UTC+1) — pour les résas confirmées du lendemain.
     * Insère une notification in-app + déclenche FCM si device_tokens présents.
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Casablanca")
    @Transactional
    public void sendReservationRemindersJ1() {
        log.info("[cron] sendReservationRemindersJ1 starting (J-1, 9h Maroc)...");
        int inserted = em.createNativeQuery("""
                INSERT INTO notifications (id, recipient_user_id, type, channel, title, body, link, created_at)
                SELECT gen_random_uuid(), r.client_id, 'reservation', 'inapp',
                       'Demain à ' || to_char(r.reservation_at AT TIME ZONE 'Africa/Casablanca', 'HH24:MI'),
                       'Votre réservation est demain. Pensez à venir à l''heure !',
                       '/pocket/oneclick?tab=suivi',
                       NOW()
                FROM reservations r
                WHERE r.status = 'confirmed'
                  AND r.deleted_at IS NULL
                  AND r.reservation_at::date = (CURRENT_DATE + INTERVAL '1 day')::date
                """).executeUpdate();
        log.info("[cron] sendReservationRemindersJ1 done: {} reminders sent", inserted);
    }

    /**
     * Rappel H-2 — toutes les 15 min, notifie les résas confirmées dans 2h.
     * Filtre anti-doublon : pas déjà notifié pour le slot H-2.
     */
    @Scheduled(cron = "0 0/15 * * * *")
    @Transactional
    public void sendReservationRemindersH2() {
        log.info("[cron] sendReservationRemindersH2 starting (H-2)...");
        int inserted = em.createNativeQuery("""
                INSERT INTO notifications (id, recipient_user_id, type, channel, title, body, link, metadata, created_at)
                SELECT gen_random_uuid(), r.client_id, 'reservation', 'push',
                       'Réservation dans 2h',
                       'À tout à l''heure ! Pensez à venir à l''heure.',
                       '/pocket/oneclick?tab=suivi',
                       jsonb_build_object('reservationId', r.id, 'slot', 'h2'),
                       NOW()
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
                """).executeUpdate();
        log.info("[cron] sendReservationRemindersH2 done: {} reminders sent", inserted);
    }
}
