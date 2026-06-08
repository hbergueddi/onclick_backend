package com.onesley.oneclick.core.membership.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Read-view native (pattern P2.c) — authorities {@code VERB:RESOURCE} octroyées par les
     * memberships ACTIVES de l'utilisateur, via leur {@code role} programme. Joint
     * {@code tenant_memberships → permissions → actions/menus} (mêmes tables que le graphe RBAC,
     * lues en SQL natif pour ne pas importer le module identity). {@code DISTINCT} : un même
     * couple action×menu peut venir de plusieurs memberships.
     */
    @Query(value = """
            SELECT DISTINCT a.code || ':' || m.code
            FROM tenant_memberships tm
            JOIN permissions p ON p.role_id = tm.role_id
            JOIN actions a ON a.id = p.action_id
            JOIN menus m ON m.id = p.menu_id
            WHERE tm.user_id = :userId
              AND tm.status = 'active'
              AND tm.deleted_at IS NULL
              AND tm.role_id IS NOT NULL
            """, nativeQuery = true)
    List<String> findActiveMembershipAuthorities(@Param("userId") UUID userId);
}
