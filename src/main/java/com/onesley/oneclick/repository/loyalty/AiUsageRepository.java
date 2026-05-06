package com.onesley.oneclick.repository.loyalty;

import com.onesley.oneclick.entity.loyalty.AiUsage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link AiUsage} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface AiUsageRepository extends JpaRepository<AiUsage, UUID>, JpaSpecificationExecutor<AiUsage> {
}
