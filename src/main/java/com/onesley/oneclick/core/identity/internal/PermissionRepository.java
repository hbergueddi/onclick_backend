package com.onesley.oneclick.core.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository {@link Permission} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID>, JpaSpecificationExecutor<Permission> {
    java.util.List<Permission> findAllByRoleId(java.util.UUID roleId);
    java.util.List<Permission> findAllByMenuId(java.util.UUID menuId);
    java.util.List<Permission> findAllByActionId(java.util.UUID actionId);

    /**
     * Bug 32 (RBAC v2) — Concatène les permissions d'un rôle au format senior
     * {@code "<action.code>:<menu.code>"} (ex: {@code "VIEW:RESTAURANTS"}).
     *
     * <p>Consommé par {@code UserRoleAuthoritiesConverter} pour exposer chaque
     * permission comme {@code GrantedAuthority} → {@code @PreAuthorize(
     * "hasAuthority('VIEW:RESTAURANTS')")} fonctionne dans les controllers.
     *
     * <p>{@code JOIN} (inner) sur menu + action — les rows {@code permissions}
     * où l'un des deux est NULL (menu-seul ou action-seule) sont silencieusement
     * exclues car non utilisables pour le pattern VERB:RESOURCE.
     */
    @Query("""
        SELECT CONCAT(a.code, ':', m.code)
          FROM Permission p
          JOIN p.action a
          JOIN p.menu m
         WHERE p.role.id = :roleId
        """)
    List<String> findAuthorityStringsByRoleId(@Param("roleId") UUID roleId);
}
