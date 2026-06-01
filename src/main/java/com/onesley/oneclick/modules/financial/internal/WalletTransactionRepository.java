package com.onesley.oneclick.modules.financial.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link WalletTransaction} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID>, JpaSpecificationExecutor<WalletTransaction> {
    java.util.List<WalletTransaction> findAllByRestaurantId(java.util.UUID restaurantId);
    java.util.List<WalletTransaction> findAllByCreatedBy(java.util.UUID createdBy);

    /**
     * B1.5 — solde wallet (SUM des mouvements) groupé par restaurant, en UNE requête
     * (remplace le fan-out N+1 de RestaurantDetailsDialog). Restaurants sans mouvement
     * absents du résultat (le caller traite l'absence comme 0).
     * Retour : {@code Object[]{UUID restaurantId, BigDecimal balance}}.
     */
    @Query("SELECT w.restaurantId, COALESCE(SUM(w.amount), 0) FROM WalletTransaction w "
        + "WHERE w.restaurantId IN :restaurantIds GROUP BY w.restaurantId")
    java.util.List<Object[]> sumBalanceByRestaurants(java.util.List<java.util.UUID> restaurantIds);
}
