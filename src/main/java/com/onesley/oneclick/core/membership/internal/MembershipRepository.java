package com.onesley.oneclick.core.membership.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository {@link TenantMembership}. Les finders excluent toujours les lignes soft-deletées
 * ({@code DeletedAtIsNull}) — invariant du schéma (cf {@code uq_tenant_memberships_user_tenant_alive}).
 */
@Repository
public interface MembershipRepository extends JpaRepository<TenantMembership, UUID> {

    boolean existsByUserIdAndTenantIdAndStatusAndDeletedAtIsNull(UUID userId, UUID tenantId, String status);

    List<TenantMembership> findAllByUserIdAndStatusAndDeletedAtIsNull(UUID userId, String status);

    Optional<TenantMembership> findByUserIdAndTenantIdAndDeletedAtIsNull(UUID userId, UUID tenantId);
}
