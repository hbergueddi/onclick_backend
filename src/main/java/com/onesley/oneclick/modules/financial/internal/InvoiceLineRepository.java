package com.onesley.oneclick.modules.financial.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link InvoiceLine} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID>, JpaSpecificationExecutor<InvoiceLine> {
    java.util.List<InvoiceLine> findAllByInvoiceId(java.util.UUID invoiceId);
}
