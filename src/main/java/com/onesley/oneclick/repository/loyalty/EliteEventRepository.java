package com.onesley.oneclick.repository.loyalty;

import com.onesley.oneclick.entity.loyalty.EliteEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link EliteEvent} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface EliteEventRepository extends JpaRepository<EliteEvent, UUID> {
}
