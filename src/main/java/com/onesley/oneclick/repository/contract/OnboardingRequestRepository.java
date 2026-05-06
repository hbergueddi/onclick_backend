package com.onesley.oneclick.repository.contract;

import com.onesley.oneclick.entity.contract.OnboardingRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link OnboardingRequest} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface OnboardingRequestRepository extends JpaRepository<OnboardingRequest, UUID>, JpaSpecificationExecutor<OnboardingRequest> {
}
