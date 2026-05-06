package com.onesley.oneclick.repository.support;

import com.onesley.oneclick.entity.support.ClientVisibleRating;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Repository read-only pour {@link ClientVisibleRating} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
public interface ClientVisibleRatingRepository extends Repository<ClientVisibleRating, UUID> {

    Optional<ClientVisibleRating> findById(UUID id);

    List<ClientVisibleRating> findAll();

    long count();
}
