package com.onesley.oneclick.modules.promotion.internal;

import com.onesley.oneclick.shared.events.OfferExpiredEvent;
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
 * Crons du module promotion — Sprint G.3 (port pg_cron legacy).
 *
 * <p>{@code expire-promotions} : désactive les offres dont {@code expires_at < NOW()}
 * pour qu'elles disparaissent des listes actives Pocket.
 *
 * <p>Tous les jours à 4h du matin (UTC, après processExpiredPoints).
 *
 * <p><b>Lot B11 (notif « offre expirée » au staff)</b> : avant, le cron faisait un pur
 * {@code UPDATE} bulk → le restaurant n'était JAMAIS prévenu qu'une de ses offres avait expiré.
 * Maintenant : on SELECT les offres au <b>passage actif→expiré</b> (id, resto, titre), on UPDATE
 * uniquement celles-là, puis on publie un {@link OfferExpiredEvent} par offre (avec le staff du
 * resto résolu côté promotion) → {@code NotificationEventHandler} crée la notif in-app staff.
 * Idempotent : le SELECT ne retient que {@code enabled = true} → une offre déjà désactivée n'est
 * jamais re-notifiée.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PromotionCronJobs {

    @PersistenceContext
    private EntityManager em;

    private final OfferRepository offerRepository;
    private final ApplicationEventPublisher events;

    @Scheduled(cron = "0 0 4 * * *", zone = "UTC")
    @Transactional
    public void expirePromotions() {
        log.info("[cron] expirePromotions starting...");
        // 1. SELECT des offres au passage actif→expiré (id, resto, titre) — AVANT l'UPDATE pour
        //    capturer celles qui étaient encore actives (idempotence : enabled = true).
        @SuppressWarnings("unchecked")
        List<Object[]> expiring = em.createNativeQuery("""
                SELECT o.id, o.restaurant_id, o.title
                FROM offers o
                WHERE o.deleted_at IS NULL
                  AND o.enabled = true
                  AND o.expires_at IS NOT NULL
                  AND o.expires_at < NOW()
                """).getResultList();
        if (expiring.isEmpty()) {
            log.info("[cron] expirePromotions done: 0 offers expirées");
            return;
        }
        List<UUID> ids = expiring.stream().map(row -> toUuid(row[0])).toList();
        int updated = em.createNativeQuery("""
                UPDATE offers
                   SET enabled = false
                 WHERE id IN (:ids)
                """).setParameter("ids", ids).executeUpdate();
        // 2. Notif staff par offre — destinataires (staff actif du resto) résolus côté promotion
        //    (requête native) et portés sur l'event (frontière Modulith). Pas d'event si aucun staff.
        for (Object[] row : expiring) {
            UUID offerId = toUuid(row[0]);
            UUID restaurantId = toUuid(row[1]);
            String title = row[2] != null ? String.valueOf(row[2]) : null;
            List<UUID> staffRecipientIds = offerRepository.findStaffRecipientIdsForRestaurant(restaurantId);
            if (staffRecipientIds.isEmpty()) continue;
            events.publishEvent(new OfferExpiredEvent(
                offerId, restaurantId, staffRecipientIds, title, Instant.now()));
        }
        log.info("[cron] expirePromotions done: {} offers expirées", updated);
    }

    /** Colonne {@code uuid} d'une requête native : le driver pg renvoie un {@link UUID}, fallback parse défensif. */
    private static UUID toUuid(Object o) {
        return o instanceof UUID u ? u : UUID.fromString(String.valueOf(o));
    }
}
