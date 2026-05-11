package com.onesley.oneclick.modules.financial;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
}
