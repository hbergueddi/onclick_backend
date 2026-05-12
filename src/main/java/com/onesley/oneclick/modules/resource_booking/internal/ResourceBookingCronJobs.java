package com.onesley.oneclick.modules.resource_booking.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crons du module resource_booking — Sprint G.3 (port pg_cron legacy).
 *
 * <p>{@code expire-unanswered-resource-bookings} : annule les bookings PCC
 * pending non répondus H-2 avant le créneau (Padel/Spa/Golf/Coiffeur/Palm Gym).
 *
 * <p>Toutes les 15 min (aligné ReservationCronJobs).
 */
@Component
public class ResourceBookingCronJobs {

    private static final Logger log = LoggerFactory.getLogger(ResourceBookingCronJobs.class);

    @PersistenceContext
    private EntityManager em;

    @Scheduled(cron = "0 0/15 * * * *")
    @Transactional
    public void expireUnansweredResourceBookings() {
        log.info("[cron] expireUnansweredResourceBookings starting...");
        int updated = em.createNativeQuery("""
                UPDATE resource_bookings
                   SET status = 'cancelled',
                       updated_at = NOW()
                 WHERE deleted_at IS NULL
                   AND status = 'pending'
                   AND start_at <= NOW() + INTERVAL '2 hours'
                """).executeUpdate();
        log.info("[cron] expireUnansweredResourceBookings done: {} bookings expirés", updated);
    }
}
