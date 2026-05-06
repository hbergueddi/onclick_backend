package com.onesley.oneclick.repository.tenant;

import com.onesley.oneclick.entity.tenant.TenantEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Repository pour {@link TenantEvent} (généré par
 * scripts/scaffold-jpa.mjs). Étendre par des queries dérivées au besoin.
 */
@Repository
public interface TenantEventRepository extends JpaRepository<TenantEvent, UUID>, JpaSpecificationExecutor<TenantEvent> {
}
