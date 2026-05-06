package com.onesley.oneclick.repository.marketing;

import com.onesley.oneclick.entity.marketing.PromoNotificationRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link PromoNotificationRequest} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface PromoNotificationRequestRepository extends JpaRepository<PromoNotificationRequest, UUID>, JpaSpecificationExecutor<PromoNotificationRequest> {
}
