package com.onesley.oneclick.repository.auth;

import com.onesley.oneclick.entity.shared.AppRole;
import com.onesley.oneclick.entity.auth.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository {@link UserRole} — accès CRUD + finders métier.
 *
 * <p>Pattern pilote : repository minimal, juste les requêtes nécessaires côté usage.
 * Spring Data dérive automatiquement le SQL depuis le nom de la méthode.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    /** Tous les rôles d'un user (1 user peut avoir plusieurs rôles, ex: client+restaurateur). */
    List<UserRole> findAllByUserId(UUID userId);

    /** Vérifie qu'un user porte un rôle donné — utilisé en autorisation. */
    Optional<UserRole> findByUserIdAndRole(UUID userId, AppRole role);

    /** Existence rapide (pas de chargement entité) — utilisé en {@code @PreAuthorize}. */
    boolean existsByUserIdAndRole(UUID userId, AppRole role);
}
