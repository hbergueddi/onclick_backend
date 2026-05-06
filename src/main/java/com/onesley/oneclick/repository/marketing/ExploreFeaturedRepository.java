package com.onesley.oneclick.repository.marketing;

import com.onesley.oneclick.entity.marketing.ExploreFeatured;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link ExploreFeatured} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface ExploreFeaturedRepository extends JpaRepository<ExploreFeatured, UUID> {
}
