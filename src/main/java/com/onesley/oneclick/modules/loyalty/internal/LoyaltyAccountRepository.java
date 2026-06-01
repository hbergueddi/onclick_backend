package com.onesley.oneclick.modules.loyalty.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link LoyaltyAccount} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface LoyaltyAccountRepository extends JpaRepository<LoyaltyAccount, UUID>, JpaSpecificationExecutor<LoyaltyAccount> {
    java.util.List<LoyaltyAccount> findAllByClientId(java.util.UUID clientId);
    java.util.List<LoyaltyAccount> findAllByRestaurantId(java.util.UUID restaurantId);
    /** P1 (anti-N+1 shim) — comptes de PLUSIEURS restaurants en 1 requête (shim client.ts:321/356 + PulsePro). */
    java.util.List<LoyaltyAccount> findAllByRestaurantIdIn(java.util.List<java.util.UUID> restaurantIds);
    java.util.List<LoyaltyAccount> findAllByTierId(java.util.UUID tierId);

    /**
     * Comptes d'un client enrichis du nom/cuisine restaurant (anti-N+1, JOIN SQL —
     * le module loyalty CLOSED ne peut pas importer l'entité Restaurant). Pocket
     * « Mes Points » / historique. Cf {@link LoyaltyAccountWithRestaurantView}.
     */
    @Query(value = """
        SELECT a.id            AS id,
               a.client_id     AS clientId,
               a.restaurant_id AS restaurantId,
               a.tier_id       AS tierId,
               a.balance       AS balance,
               a.created_at    AS createdAt,
               r.name          AS restaurantName,
               r.cuisine       AS restaurantCuisine
        FROM loyalty_accounts a
        LEFT JOIN restaurants r ON r.id = a.restaurant_id
        WHERE a.client_id = :clientId
        """, nativeQuery = true)
    java.util.List<LoyaltyAccountWithRestaurantView> findAllByClientIdWithRestaurant(@Param("clientId") java.util.UUID clientId);
}
