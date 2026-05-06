package com.onesley.oneclick.repository.tenant;

import com.onesley.oneclick.entity.tenant.TenantFeature;
import com.onesley.oneclick.entity.tenant.TenantFeatureId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link TenantFeature} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface TenantFeatureRepository extends JpaRepository<TenantFeature, TenantFeatureId> {
}
