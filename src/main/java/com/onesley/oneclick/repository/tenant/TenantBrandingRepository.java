package com.onesley.oneclick.repository.tenant;

import com.onesley.oneclick.entity.tenant.TenantBranding;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link TenantBranding} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface TenantBrandingRepository extends JpaRepository<TenantBranding, UUID>, JpaSpecificationExecutor<TenantBranding> {
}
