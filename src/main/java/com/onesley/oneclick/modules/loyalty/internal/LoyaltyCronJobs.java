package com.onesley.oneclick.modules.loyalty.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Crons du module loyalty — Sprint G.3 (port pg_cron legacy).
 *
 * <p>{@code process-expired-points} : insère des transactions {@code type='expire'}
 * (points négatifs) pour les points dont {@code expires_at < NOW()} et qui
 * n'ont pas encore été expirés. Met aussi à jour le balance du compte.
 *
 * <p>Tous les jours à 3h du matin (heure UTC, après les rushs européens).
 */
@Component
@Slf4j
public class LoyaltyCronJobs {

    @PersistenceContext
    private EntityManager em;

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    @Transactional
    public void processExpiredPoints() {
        log.info("[cron] processExpiredPoints starting...");

        // 1. Identifier les transactions 'earn' avec expires_at < NOW()
        //    qui n'ont pas encore été matchées par une transaction 'expire'
        //    (jointure self-anti pour anti-doublon).
        @SuppressWarnings("unchecked")
        var expired = em.createNativeQuery("""
                SELECT t.id, t.account_id, t.points
                FROM loyalty_transactions t
                WHERE t.type = 'earn'
                  AND t.expires_at IS NOT NULL
                  AND t.expires_at < NOW()
                  AND NOT EXISTS (
                      SELECT 1 FROM loyalty_transactions e
                      WHERE e.type = 'expire'
                        AND e.reason LIKE 'auto:expired:' || t.id || '%'
                  )
                LIMIT 5000
                """).getResultList();

        int processed = 0;
        for (Object row : expired) {
            Object[] cols = (Object[]) row;
            java.util.UUID earnId = (java.util.UUID) cols[0];
            java.util.UUID accountId = (java.util.UUID) cols[1];
            Integer points = ((Number) cols[2]).intValue();
            if (points <= 0) continue;

            // 2. Insère la transaction d'expiration (points négatifs)
            em.createNativeQuery("""
                    INSERT INTO loyalty_transactions
                        (id, account_id, type, points, reason, created_at)
                    VALUES
                        (gen_random_uuid(), :accountId, 'expire', :pts, 'auto:expired:' || :earnId, NOW())
                    """)
                .setParameter("accountId", accountId)
                .setParameter("pts", -points)
                .setParameter("earnId", earnId)
                .executeUpdate();

            // 3. Update balance du compte
            em.createNativeQuery("""
                    UPDATE loyalty_accounts
                       SET balance = balance - :pts
                     WHERE id = :accountId
                    """)
                .setParameter("pts", points)
                .setParameter("accountId", accountId)
                .executeUpdate();

            processed++;
        }
        log.info("[cron] processExpiredPoints done: {} earn transactions expirées", processed);
    }
}
