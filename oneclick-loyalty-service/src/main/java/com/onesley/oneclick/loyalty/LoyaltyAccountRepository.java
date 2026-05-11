package com.onesley.oneclick.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
    java.util.List<LoyaltyAccount> findAllByTierId(java.util.UUID tierId);
}
