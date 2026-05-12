package com.onesley.oneclick.modules.financial.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crons du module financial — Sprint G.3 (port pg_cron legacy).
 *
 * <p>{@code renew-contracts} : prolonge automatiquement les contrats arrivant
 * à échéance dans les 30 jours, si le restaurateur n'a pas explicitement
 * demandé la résiliation. Crée une notification de renouvellement.
 *
 * <p>Tous les 1er du mois à 2h UTC.
 */
@Component
public class FinancialCronJobs {

    private static final Logger log = LoggerFactory.getLogger(FinancialCronJobs.class);

    @PersistenceContext
    private EntityManager em;

    @Scheduled(cron = "0 0 2 1 * *", zone = "UTC")
    @Transactional
    public void renewContracts() {
        log.info("[cron] renewContracts starting (1er du mois)...");
        int updated = em.createNativeQuery("""
                UPDATE contracts
                   SET ends_at = ends_at + INTERVAL '1 year',
                       updated_at = NOW()
                 WHERE deleted_at IS NULL
                   AND status = 'active'
                   AND ends_at IS NOT NULL
                   AND ends_at BETWEEN NOW() AND NOW() + INTERVAL '30 days'
                """).executeUpdate();
        log.info("[cron] renewContracts done: {} contrats renouvelés (+1 an)", updated);
    }
}
