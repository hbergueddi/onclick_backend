package com.onesley.oneclick.repository.restaurant;

import com.onesley.oneclick.entity.restaurant.RestaurantsConfigView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Repository read-only pour {@link RestaurantsConfigView} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
public interface RestaurantsConfigViewRepository extends Repository<RestaurantsConfigView, UUID> {

    Optional<RestaurantsConfigView> findById(UUID id);

    List<RestaurantsConfigView> findAll();

    long count();
}
