package com.onesley.oneclick.repository.misc;

import com.onesley.oneclick.entity.misc.LoyaltyPlafond;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link LoyaltyPlafond} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface LoyaltyPlafondRepository extends JpaRepository<LoyaltyPlafond, UUID> {
}
