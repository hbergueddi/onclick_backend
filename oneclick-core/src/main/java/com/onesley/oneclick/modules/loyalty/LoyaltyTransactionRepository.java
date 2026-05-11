package com.onesley.oneclick.modules.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link LoyaltyTransaction} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface LoyaltyTransactionRepository extends JpaRepository<LoyaltyTransaction, UUID>, JpaSpecificationExecutor<LoyaltyTransaction> {
    java.util.List<LoyaltyTransaction> findAllByAccountId(java.util.UUID accountId);
    java.util.List<LoyaltyTransaction> findAllByCreatedBy(java.util.UUID createdBy);
}
