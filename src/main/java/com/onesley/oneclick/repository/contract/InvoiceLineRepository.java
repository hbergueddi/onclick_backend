package com.onesley.oneclick.repository.contract;

import com.onesley.oneclick.entity.contract.InvoiceLine;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link InvoiceLine} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {
}
