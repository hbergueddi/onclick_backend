package com.onesley.oneclick.repository.loyalty;

import com.onesley.oneclick.entity.loyalty.RedemptionEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link RedemptionEvent} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface RedemptionEventRepository extends JpaRepository<RedemptionEvent, UUID>, JpaSpecificationExecutor<RedemptionEvent> {
}
