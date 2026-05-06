package com.onesley.oneclick.repository.admin;

import com.onesley.oneclick.entity.admin.ActionLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link ActionLog} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface ActionLogRepository extends JpaRepository<ActionLog, UUID> {
}
