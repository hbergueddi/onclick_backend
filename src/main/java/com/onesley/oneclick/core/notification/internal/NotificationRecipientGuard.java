package com.onesley.oneclick.core.notification.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Garde-fou d'intégrité du destinataire d'une notification.
 *
 * <p>Vérifie qu'un {@code user} existe AVANT d'insérer une ligne {@code notifications}, pour éviter la
 * violation de FK {@code notifications.recipient_user_id → users}. Sans ce garde-fou, un destinataire
 * supprimé (cas typique : <b>events Spring Modulith dormants rejoués au démarrage</b> alors que le
 * compte a été supprimé entre-temps) faisait échouer le listener <b>au COMMIT</b> (FK différée, donc
 * non rattrapable par un try/catch autour de {@code create()}), laissant la publication d'event
 * incomplète → rejouée en boucle à chaque restart.
 *
 * <p>Lecture <b>SQL native</b> sur la table {@code users} : {@code core.notification} est un module
 * Modulith CLOSED qui ne dépend pas de {@code core.identity} — on lit le read-model {@code users}
 * sans franchir la frontière de <i>type</i> Java (même pattern que les autres lectures read-model
 * inter-modules du projet). Aucune dépendance de module ajoutée.
 */
@Component
class NotificationRecipientGuard {

    @PersistenceContext
    private EntityManager em;

    /** {@code true} si {@code userId} est présent dans {@code users} (FK satisfiable). {@code false} si null/absent. */
    boolean exists(UUID userId) {
        if (userId == null) return false;
        Number count = (Number) em.createNativeQuery("SELECT count(*) FROM users WHERE id = :id")
            .setParameter("id", userId)
            .getSingleResult();
        return count.intValue() > 0;
    }
}
