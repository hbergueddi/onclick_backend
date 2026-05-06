package com.onesley.oneclick.repository.admin;

import com.onesley.oneclick.entity.admin.DocumentVersion;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link DocumentVersion} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID>, JpaSpecificationExecutor<DocumentVersion> {
}
