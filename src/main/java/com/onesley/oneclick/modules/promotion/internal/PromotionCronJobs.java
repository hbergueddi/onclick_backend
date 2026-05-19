package com.onesley.oneclick.modules.promotion.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Crons du module promotion — Sprint G.3 (port pg_cron legacy).
 *
 * <p>{@code expire-promotions} : désactive les offres dont {@code expires_at < NOW()}
 * pour qu'elles disparaissent des listes actives Pocket.
 *
 * <p>Tous les jours à 4h du matin (UTC, après processExpiredPoints).
 */
@Component
@Slf4j
public class PromotionCronJobs {

    @PersistenceContext
    private EntityManager em;

    @Scheduled(cron = "0 0 4 * * *", zone = "UTC")
    @Transactional
    public void expirePromotions() {
        log.info("[cron] expirePromotions starting...");
        int updated = em.createNativeQuery("""
                UPDATE offers
                   SET enabled = false
                 WHERE deleted_at IS NULL
                   AND enabled = true
                   AND expires_at IS NOT NULL
                   AND expires_at < NOW()
                """).executeUpdate();
        log.info("[cron] expirePromotions done: {} offers expirées", updated);
    }
}
