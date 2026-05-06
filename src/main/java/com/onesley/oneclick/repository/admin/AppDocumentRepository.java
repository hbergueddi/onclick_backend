package com.onesley.oneclick.repository.admin;

import com.onesley.oneclick.entity.admin.AppDocument;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link AppDocument} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface AppDocumentRepository extends JpaRepository<AppDocument, UUID> {
}
