package com.onesley.oneclick.repository.loyalty;

import com.onesley.oneclick.entity.loyalty.GainRule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link GainRule} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface GainRuleRepository extends JpaRepository<GainRule, UUID> {
}
