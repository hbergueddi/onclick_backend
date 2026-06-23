package com.onesley.oneclick.modules.announcement.internal;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cron du module announcement — Lot B13 (publication différée des annonces programmées).
 *
 * <p>Une annonce peut être créée avec {@code publish_at} futur (programmée) : à la création, le
 * service NE notifie PAS le staff (cf. {@code AnnouncementService.create} — l'event n'est publié que
 * si {@code publish_at <= now}). Ce cron prend le relais : toutes les minutes, il publie les annonces
 * dont {@code publish_at <= now()}, vivantes et <b>jamais notifiées</b> ({@code push_sent_at IS NULL}),
 * en réutilisant exactement le chemin de publication immédiate (event {@code AnnouncementPublishedEvent}
 * → notif in-app + push staff via B15a + push STOMP) puis stampe {@code push_sent_at}.
 *
 * <p><b>Idempotence</b> : le filtre {@code push_sent_at IS NULL} (porté par le partial index V70
 * {@code idx_tenant_announcements_pending}) garantit qu'une annonce n'est notifiée qu'une seule fois,
 * même si le cron re-tourne (crash/restart) ou se chevauche. Aucune migration nécessaire — la colonne
 * {@code push_sent_at} et son index existaient déjà (V70, réservés « V1 hors scope »).
 *
 * <p>Pattern aligné sur {@code PromotionCronJobs}/{@code ReservationCronJobs} : {@code @Scheduled} +
 * {@code @Transactional} (porté par le service) + log INFO start/done. Toutes les minutes : une
 * annonce programmée doit partir au plus près de son {@code publish_at} (granularité minute suffisante
 * pour une comm B2B descendante).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AnnouncementCronJobs {

    private final AnnouncementService announcementService;

    /** Toutes les minutes (seconde 0) — publie les annonces programmées arrivées à échéance. */
    @Scheduled(cron = "0 * * * * *")
    public void publishDueAnnouncements() {
        int published = announcementService.publishDueScheduled();
        if (published > 0) {
            log.info("[cron] publishDueAnnouncements done: {} annonce(s) programmée(s) publiée(s)", published);
        }
    }
}
