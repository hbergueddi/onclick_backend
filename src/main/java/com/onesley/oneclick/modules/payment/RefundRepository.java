package com.onesley.oneclick.modules.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link Refund} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID>, JpaSpecificationExecutor<Refund> {
    java.util.List<Refund> findAllByPaymentId(java.util.UUID paymentId);
    java.util.List<Refund> findAllByCreatedById(java.util.UUID createdBy);
}
