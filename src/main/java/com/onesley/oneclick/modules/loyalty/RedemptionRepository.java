package com.onesley.oneclick.modules.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link Redemption} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface RedemptionRepository extends JpaRepository<Redemption, UUID>, JpaSpecificationExecutor<Redemption> {
    java.util.List<Redemption> findAllByAccountId(java.util.UUID accountId);
    java.util.List<Redemption> findAllByCreatedById(java.util.UUID createdBy);
}
