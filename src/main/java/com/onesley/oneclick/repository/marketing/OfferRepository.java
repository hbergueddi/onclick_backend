package com.onesley.oneclick.repository.marketing;

import com.onesley.oneclick.entity.marketing.Offer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link Offer} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface OfferRepository extends JpaRepository<Offer, UUID> {
}
