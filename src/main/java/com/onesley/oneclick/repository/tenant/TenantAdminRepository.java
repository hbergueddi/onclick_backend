package com.onesley.oneclick.repository.tenant;

import com.onesley.oneclick.entity.tenant.TenantAdmin;
import com.onesley.oneclick.entity.tenant.TenantAdminId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantAdminRepository extends JpaRepository<TenantAdmin, TenantAdminId> {

    /** Tous les admins d'un tenant. */
    List<TenantAdmin> findAllByTenantId(UUID tenantId);

    /** Tous les tenants où un user est admin. */
    List<TenantAdmin> findAllByUserId(UUID userId);

    /** Lookup unique par couple (tenant, user) — pour la vérification de droits. */
    Optional<TenantAdmin> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    boolean existsByTenantIdAndUserId(UUID tenantId, UUID userId);
}
