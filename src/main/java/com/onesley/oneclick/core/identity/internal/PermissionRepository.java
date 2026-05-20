package com.onesley.oneclick.core.identity.internal;
import com.onesley.oneclick.core.identity.api.Permission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link Permission} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 *
 * <p>Bug 34 — la méthode {@code findAuthorityStringsByRoleId} (Bug 32) a été
 * supprimée en même temps que {@code AuthoritiesProvider} : les authorities
 * sont désormais construites en mémoire dans {@link com.onesley.oneclick.security.OneClickUserDetails#from}
 * à partir du graphe {@code role.permissions} chargé par
 * {@code UserRepository.findByIdWithRoleAndPermissions} (JOIN FETCH unique).
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID>, JpaSpecificationExecutor<Permission> {
    java.util.List<Permission> findAllByRoleId(java.util.UUID roleId);
    java.util.List<Permission> findAllByMenuId(java.util.UUID menuId);
    java.util.List<Permission> findAllByActionId(java.util.UUID actionId);

    /** Remplace-la-grille : on supprime toutes les permissions d'un rôle avant ré-insertion. */
    @Modifying
    void deleteByRoleId(java.util.UUID roleId);
}
