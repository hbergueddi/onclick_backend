package com.onesley.oneclick.repository.restaurant;

import com.onesley.oneclick.entity.restaurant.RestaurantRestitution;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link RestaurantRestitution} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface RestaurantRestitutionRepository extends JpaRepository<RestaurantRestitution, UUID>, JpaSpecificationExecutor<RestaurantRestitution> {
}
