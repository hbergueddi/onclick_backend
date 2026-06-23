package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.shared.events.ResourceBookingReminderDueEvent;
import com.onesley.oneclick.shared.events.ResourceBookingStatusChangedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Crons du module resource_booking — Sprint G.3 (port pg_cron legacy) + gaps #4/#5.
 *
 * <ul>
 *   <li><b>#5</b> {@code expire-unanswered-resource-bookings} : annule les bookings PCC
 *     {@code pending} non répondus H-2 ET publie un {@link ResourceBookingStatusChangedEvent}
 *     ({@code changedBy=null} → annulation système) par booking → le client est notifié
 *     (in-app + push) par {@code NotificationEventHandler} (avant : UPDATE muet, 0 notif).</li>
 *   <li><b>#4</b> {@code send-pcc-reservation-reminders} J-1 / H-2 : publie un
 *     {@link ResourceBookingReminderDueEvent} par booking confirmé dû → notif in-app
 *     (metadata anti-doublon H-2) + push (parité legacy {@code send-pcc-reservation-reminders}).</li>
 * </ul>
 *
 * <p>Frontière Modulith : {@code resource_booking} ne dépend pas de {@code core.notification} —
 * communication via events dans {@code shared.events}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ResourceBookingCronJobs {

    private static final String REMINDER_LINK = "/pocket/oneclick?tab=suivi";

    @PersistenceContext
    private EntityManager em;

    private final ApplicationEventPublisher events;

    /**
     * Gap #5 — annule les bookings {@code pending} non répondus à H-2 et notifie le client.
     * SELECT (id + contexte via JOIN resources) → UPDATE → publie un event d'annulation par row
     * ({@code changedBy=null} = système → le handler ne skip pas, contrairement à une auto-annulation
     * du membre). Toutes les 15 min (aligné ReservationCronJobs).
     */
    @Scheduled(cron = "0 0/15 * * * *")
    @Transactional
    public void expireUnansweredResourceBookings() {
        log.info("[cron] expireUnansweredResourceBookings starting...");
        @SuppressWarnings("unchecked")
        List<Object[]> due = em.createNativeQuery("""
                SELECT b.id, b.organizer_id, b.resource_id, r.tenant_id, r.resource_type, b.status
                FROM resource_bookings b
                JOIN resources r ON r.id = b.resource_id
                WHERE b.deleted_at IS NULL
                  AND b.status = 'pending'
                  AND b.start_at <= NOW() + INTERVAL '2 hours'
                """).getResultList();
        if (due.isEmpty()) {
            log.info("[cron] expireUnansweredResourceBookings done: 0 bookings expirés");
            return;
        }
        List<UUID> ids = due.stream().map(row -> toUuid(row[0])).toList();
        int updated = em.createNativeQuery("""
                UPDATE resource_bookings
                   SET status = 'cancelled', updated_at = NOW()
                 WHERE id IN (:ids)
                """).setParameter("ids", ids).executeUpdate();
        for (Object[] row : due) {
            events.publishEvent(new ResourceBookingStatusChangedEvent(
                toUuid(row[0]), toUuid(row[1]), toUuid(row[2]), toUuid(row[3]),
                String.valueOf(row[4]), "pending", "cancelled",
                null,             // changedBy=null → annulation système (push client, pas de skip)
                Instant.now()));
        }
        log.info("[cron] expireUnansweredResourceBookings done: {} bookings expirés", updated);
    }

    /**
     * Gap #4 — rappel J-1 à 9h Maroc pour les bookings confirmés du lendemain.
     * Publie un {@link ResourceBookingReminderDueEvent} par organisateur.
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Casablanca")
    @Transactional
    public void sendResourceBookingRemindersJ1() {
        log.info("[cron] sendResourceBookingRemindersJ1 starting (J-1, 9h Maroc)...");
        @SuppressWarnings("unchecked")
        List<Object[]> due = em.createNativeQuery("""
                SELECT b.organizer_id, b.id,
                       to_char(b.start_at AT TIME ZONE 'Africa/Casablanca', 'HH24:MI') AS hhmm
                FROM resource_bookings b
                WHERE b.status = 'confirmed'
                  AND b.deleted_at IS NULL
                  AND b.start_at::date = (CURRENT_DATE + INTERVAL '1 day')::date
                """).getResultList();
        for (Object[] row : due) {
            events.publishEvent(new ResourceBookingReminderDueEvent(
                toUuid(row[0]), toUuid(row[1]), "j1",
                "Demain à " + row[2],
                "Votre activité au club est demain. À bientôt !",
                REMINDER_LINK, Instant.now()));
        }
        log.info("[cron] sendResourceBookingRemindersJ1 done: {} reminders published", due.size());
    }

    /**
     * Gap #4 — rappel H-2 (toutes les 15 min) pour les bookings confirmés dans ~2h.
     * Anti-doublon : exclut les bookings déjà notifiés ce slot (metadata {@code bookingId} + {@code slot='h2'}).
     */
    @Scheduled(cron = "0 0/15 * * * *")
    @Transactional
    public void sendResourceBookingRemindersH2() {
        log.info("[cron] sendResourceBookingRemindersH2 starting (H-2)...");
        @SuppressWarnings("unchecked")
        List<Object[]> due = em.createNativeQuery("""
                SELECT b.organizer_id, b.id
                FROM resource_bookings b
                WHERE b.status = 'confirmed'
                  AND b.deleted_at IS NULL
                  AND b.start_at BETWEEN NOW() + INTERVAL '1 hour 45 minutes'
                                     AND NOW() + INTERVAL '2 hours 15 minutes'
                  AND NOT EXISTS (
                      SELECT 1 FROM notifications n
                      WHERE n.recipient_user_id = b.organizer_id
                        AND n.created_at > NOW() - INTERVAL '3 hours'
                        AND n.metadata->>'bookingId' = b.id::text
                        AND n.metadata->>'slot' = 'h2'
                  )
                """).getResultList();
        for (Object[] row : due) {
            events.publishEvent(new ResourceBookingReminderDueEvent(
                toUuid(row[0]), toUuid(row[1]), "h2",
                "Activité dans 2h",
                "À tout à l'heure au club ! Pensez à arriver à l'heure.",
                REMINDER_LINK, Instant.now()));
        }
        log.info("[cron] sendResourceBookingRemindersH2 done: {} reminders published", due.size());
    }

    /** Colonne {@code uuid} d'une requête native : le driver pg renvoie un {@link UUID}, fallback parse défensif. */
    private static UUID toUuid(Object o) {
        return o instanceof UUID u ? u : UUID.fromString(String.valueOf(o));
    }
}
