package com.onesley.oneclick;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Politique de complétion défensive des publications d'events Spring Modulith « bloquées »
 * (filet GÉNÉRIQUE contre le poison de rejeu — racine systémique).
 *
 * <p><b>Contexte.</b> Avec {@code spring.modulith.events.republish-outstanding-events-on-restart=true},
 * toute publication restée <b>incomplète</b> (listener async en échec) est rejouée à chaque restart
 * (recovery après crash). Si la cause est <b>permanente</b> — typiquement l'agrégat référencé a été
 * supprimé entre-temps → violation de FK irrécupérable — l'event ne se complétera JAMAIS et pollue les
 * logs en boucle à chaque démarrage.
 *
 * <p><b>Rôle.</b> Les garde-fous par chemin ({@code NotificationRecipientGuard},
 * {@code LoyaltyEventRefGuard}) rendent les listeners CONNUS résilients (skip → complétion propre).
 * Ce reaper est le filet de sécurité <b>générique</b> : au-delà d'une période de grâce, toute
 * publication encore incomplète (n'importe quel listener / event, y compris futurs non gardés) est
 * considérée définitivement bloquée et <b>clôturée</b> ({@code completion_date} posé) pour stopper le
 * rejeu. La grâce ({@code app.modulith.stuck-event-grace-days}, défaut 3 j) laisse aux échecs
 * <i>transitoires</i> le temps d'être rejoués normalement avant abandon.
 *
 * <p>Composant d'infrastructure placé au package racine (hors module Modulith). Écrit la table
 * {@code event_publication} (gérée par Flyway V8) en SQL natif. Job de maintenance nocturne — ce
 * n'est pas un dashboard, donc l'usage d'un cron (et non d'un WebSocket) est légitime.
 */
@Component
@Slf4j
class StuckEventPublicationReaper {

    @PersistenceContext
    private EntityManager em;

    @Value("${app.modulith.stuck-event-grace-days:3}")
    private int graceDays;

    /** Nocturne 03:30 Africa/Casablanca (heure creuse). */
    @Scheduled(cron = "0 30 3 * * *", zone = "Africa/Casablanca")
    @Transactional
    public int reapStuckPublications() {
        int reaped = em.createNativeQuery(
                "UPDATE event_publication SET completion_date = now() " +
                "WHERE completion_date IS NULL " +
                "  AND publication_date < now() - (:days * interval '1 day')")
            .setParameter("days", graceDays)
            .executeUpdate();
        if (reaped > 0) {
            log.warn("[modulith-reaper] {} publication(s) d'event bloquée(s) depuis > {}j clôturée(s) "
                + "(poison non-complétable — listener en échec permanent, agrégat probablement supprimé)",
                reaped, graceDays);
        }
        return reaped;
    }
}
