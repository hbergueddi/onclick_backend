package com.onesley.oneclick.repository.contract;

import com.onesley.oneclick.entity.contract.CompanySetting;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link CompanySetting} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface CompanySettingRepository extends JpaRepository<CompanySetting, UUID>, JpaSpecificationExecutor<CompanySetting> {
}
