package com.onesley.oneclick.repository.restaurant;

import com.onesley.oneclick.entity.restaurant.RestaurantsWithGroupView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Repository read-only pour {@link RestaurantsWithGroupView} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
public interface RestaurantsWithGroupViewRepository extends Repository<RestaurantsWithGroupView, UUID> {

    Optional<RestaurantsWithGroupView> findById(UUID id);

    List<RestaurantsWithGroupView> findAll();

    long count();
}
