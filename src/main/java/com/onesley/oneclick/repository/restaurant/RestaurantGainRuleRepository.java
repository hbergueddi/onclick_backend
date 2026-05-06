package com.onesley.oneclick.repository.restaurant;

import com.onesley.oneclick.entity.restaurant.RestaurantGainRule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link RestaurantGainRule} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface RestaurantGainRuleRepository extends JpaRepository<RestaurantGainRule, UUID>, JpaSpecificationExecutor<RestaurantGainRule> {
}
