package com.onesley.oneclick.repository.support;

import com.onesley.oneclick.entity.support.ClientRating;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link ClientRating} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface ClientRatingRepository extends JpaRepository<ClientRating, UUID> {
}
