package com.onesley.oneclick.repository.admin;

import com.onesley.oneclick.entity.admin.ContactImportEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link ContactImportEvent} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface ContactImportEventRepository extends JpaRepository<ContactImportEvent, UUID>, JpaSpecificationExecutor<ContactImportEvent> {
}
