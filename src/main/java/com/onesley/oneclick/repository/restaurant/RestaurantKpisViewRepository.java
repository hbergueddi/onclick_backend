package com.onesley.oneclick.repository.restaurant;

import com.onesley.oneclick.entity.restaurant.RestaurantKpisView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Repository read-only pour {@link RestaurantKpisView} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
public interface RestaurantKpisViewRepository extends Repository<RestaurantKpisView, UUID> {

    Optional<RestaurantKpisView> findById(UUID id);

    List<RestaurantKpisView> findAll();

    long count();
}
