package com.onesley.oneclick.repository.contract;

import com.onesley.oneclick.entity.contract.PartnerContract;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link PartnerContract} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface PartnerContractRepository extends JpaRepository<PartnerContract, UUID>, JpaSpecificationExecutor<PartnerContract> {
}
