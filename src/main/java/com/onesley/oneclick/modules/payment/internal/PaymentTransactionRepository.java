package com.onesley.oneclick.modules.payment.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link PaymentTransaction} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID>, JpaSpecificationExecutor<PaymentTransaction> {
    java.util.List<PaymentTransaction> findAllByPaymentId(java.util.UUID paymentId);
}
