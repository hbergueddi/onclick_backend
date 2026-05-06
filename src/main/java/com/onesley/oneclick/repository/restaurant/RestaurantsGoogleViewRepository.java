package com.onesley.oneclick.repository.restaurant;

import com.onesley.oneclick.entity.restaurant.RestaurantsGoogleView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Repository read-only pour {@link RestaurantsGoogleView} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
public interface RestaurantsGoogleViewRepository extends Repository<RestaurantsGoogleView, UUID> {

    Optional<RestaurantsGoogleView> findById(UUID id);

    List<RestaurantsGoogleView> findAll();

    long count();
}
