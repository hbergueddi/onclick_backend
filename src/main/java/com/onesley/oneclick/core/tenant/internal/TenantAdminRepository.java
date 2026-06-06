package com.onesley.oneclick.core.tenant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository {@link TenantAdmin} — finders dérivés par tenant / couple (tenant, user).
 */
@Repository
public interface TenantAdminRepository extends JpaRepository<TenantAdmin, UUID> {

    /** Admins d'un tenant, du plus récent au plus ancien. */
    List<TenantAdmin> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<TenantAdmin> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserId(UUID tenantId, UUID userId);
}
