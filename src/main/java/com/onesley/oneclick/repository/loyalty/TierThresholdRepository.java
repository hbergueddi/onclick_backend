package com.onesley.oneclick.repository.loyalty;

import com.onesley.oneclick.entity.loyalty.TierThreshold;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link TierThreshold} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface TierThresholdRepository extends JpaRepository<TierThreshold, UUID>, JpaSpecificationExecutor<TierThreshold> {
}
