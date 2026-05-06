package com.onesley.oneclick.repository.admin;

import com.onesley.oneclick.entity.admin.QuotaChangeLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link QuotaChangeLog} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface QuotaChangeLogRepository extends JpaRepository<QuotaChangeLog, UUID>, JpaSpecificationExecutor<QuotaChangeLog> {
}
