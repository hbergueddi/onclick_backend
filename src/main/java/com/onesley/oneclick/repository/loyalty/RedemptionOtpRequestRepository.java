package com.onesley.oneclick.repository.loyalty;

import com.onesley.oneclick.entity.loyalty.RedemptionOtpRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link RedemptionOtpRequest} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface RedemptionOtpRequestRepository extends JpaRepository<RedemptionOtpRequest, UUID>, JpaSpecificationExecutor<RedemptionOtpRequest> {
}
