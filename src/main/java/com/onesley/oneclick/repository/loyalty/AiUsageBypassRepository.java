package com.onesley.oneclick.repository.loyalty;

import com.onesley.oneclick.entity.loyalty.AiUsageBypass;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link AiUsageBypass} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface AiUsageBypassRepository extends JpaRepository<AiUsageBypass, UUID> {
}
