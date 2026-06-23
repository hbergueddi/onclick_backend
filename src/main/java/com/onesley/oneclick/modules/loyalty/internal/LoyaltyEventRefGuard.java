package com.onesley.oneclick.modules.loyalty.internal;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Garde-fou d'intégrité des références portées par les events réservation, avant d'écrire une
 * ligne {@code client_ratings} (FK {@code reservation_id → reservations} et {@code user_id → users}).
 *
 * <p>Même classe de bug que {@code NotificationRecipientGuard} : un event Spring Modulith
 * <b>dormant rejoué au démarrage</b> peut référencer une réservation/un user <b>supprimés</b>
 * entre-temps → l'insert violerait la FK <b>au COMMIT</b> (différée, donc non rattrapable par un
 * try/catch dans le listener) → le listener échoue → la publication d'event reste incomplète →
 * rejouée en boucle à chaque restart.
 *
 * <p>Lecture <b>SQL native</b> (le module loyalty lit les read-models {@code reservations}/{@code users}
 * sans dépendance de type — pattern établi, cf {@code PromoAudienceResolver}).
 */
@Component
class LoyaltyEventRefGuard {

    @PersistenceContext
    private EntityManager em;

    /**
     * {@code true} si les FK sont satisfiables : {@code userId} présent dans {@code users}
     * (s'il est non-null) ET {@code reservationId} présent dans {@code reservations} (s'il est non-null).
     * Un id null = pas de contrainte FK (colonne nullable) → non bloquant.
     */
    boolean refsExist(UUID userId, UUID reservationId) {
        if (userId != null && countById("users", userId) == 0) return false;
        if (reservationId != null && countById("reservations", reservationId) == 0) return false;
        return true;
    }

    private long countById(String table, UUID id) {
        // table = littéral interne (jamais une entrée utilisateur) → pas d'injection possible.
        Number n = (Number) em.createNativeQuery("SELECT count(*) FROM " + table + " WHERE id = :id")
            .setParameter("id", id)
            .getSingleResult();
        return n.longValue();
    }
}
