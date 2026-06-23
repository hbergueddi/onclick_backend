package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.shared.events.PointsExpiringSoonEvent;
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
 * Crons du module loyalty — Sprint G.3 (port pg_cron legacy).
 *
 * <p>{@code process-expired-points} : insère des transactions {@code type='expire'}
 * (points négatifs) pour les points dont {@code expires_at < NOW()} et qui
 * n'ont pas encore été expirés. Met aussi à jour le balance du compte.
 *
 * <p>{@code alertExpiringPoints} (Feature A, parité legacy {@code notify-expiring-points}) :
 * <b>prévient</b> le client AVANT l'expiration (J-30/J-15/J-7) — publie un
 * {@link PointsExpiringSoonEvent} par compte dû ; {@code NotificationEventHandler} crée la notif
 * in-app (avec metadata anti-doublon) + push FCM. Frontière Modulith : loyalty ne dépend pas de
 * core.notification (signal via le package OPEN {@code shared.events}).
 *
 * <p>Schedules en heure Maroc pour les rappels client (matin) ; l'expiration technique reste à 3h UTC.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LoyaltyCronJobs {

    /** Jalons d'alerte avant expiration : {jours, code milestone}. */
    private static final int[] MILESTONE_DAYS = {30, 15, 7};

    @PersistenceContext
    private EntityManager em;

    private final ApplicationEventPublisher events;

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

    /**
     * Alerte « vos points expirent bientôt » (Feature A — parité legacy {@code notify-expiring-points}).
     *
     * <p>Pour chaque jalon (J-30/J-15/J-7), sélectionne les transactions {@code earn} dont
     * {@code expires_at::date} tombe exactement à ce jalon, agrégées par compte fidélité, et
     * <b>pas encore alertées</b> pour ce jalon (anti-doublon via {@code notifications.metadata}).
     * Publie un {@link PointsExpiringSoonEvent} par compte ; le listener notification crée la notif
     * in-app (avec metadata) + push FCM. Tous les matins à 9h heure Maroc.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Africa/Casablanca")
    @Transactional
    public void alertExpiringPoints() {
        log.info("[cron] alertExpiringPoints starting (J-30/J-15/J-7)...");
        int published = 0;
        for (int days : MILESTONE_DAYS) {
            String milestone = "j" + days;
            @SuppressWarnings("unchecked")
            List<Object[]> due = em.createNativeQuery("""
                    SELECT la.client_id, t.account_id, SUM(t.points) AS pts,
                           to_char((CURRENT_DATE + CAST(:days AS int)), 'DD/MM') AS expires_label
                    FROM loyalty_transactions t
                    JOIN loyalty_accounts la ON la.id = t.account_id AND la.deleted_at IS NULL
                    WHERE t.type = 'earn'
                      AND t.expires_at IS NOT NULL
                      AND t.expires_at::date = (CURRENT_DATE + CAST(:days AS int))
                      AND la.client_id IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM notifications n
                          WHERE n.recipient_user_id = la.client_id
                            AND n.metadata->>'kind' = 'points_expiring'
                            AND n.metadata->>'accountId' = t.account_id::text
                            AND n.metadata->>'milestone' = :milestone
                      )
                    GROUP BY la.client_id, t.account_id
                    HAVING SUM(t.points) > 0
                    """)
                .setParameter("days", days)
                .setParameter("milestone", milestone)
                .getResultList();
            for (Object[] row : due) {
                UUID clientId = toUuid(row[0]);
                UUID accountId = toUuid(row[1]);
                int pts = ((Number) row[2]).intValue();
                String label = String.valueOf(row[3]);
                events.publishEvent(new PointsExpiringSoonEvent(
                    clientId, accountId, pts, label, milestone, Instant.now()));
                published++;
            }
        }
        log.info("[cron] alertExpiringPoints done: {} alerts published", published);
    }

    /** Colonne {@code uuid} d'une requête native : le driver pg renvoie un {@link UUID}, fallback parse défensif. */
    private static UUID toUuid(Object o) {
        return o instanceof UUID u ? u : UUID.fromString(String.valueOf(o));
    }
}
