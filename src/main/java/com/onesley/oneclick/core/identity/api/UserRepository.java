package com.onesley.oneclick.core.identity.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import com.onesley.oneclick.core.identity.api.User;

/**
 * Repository {@link User} — accès CRUD + finders métier.
 *
 * <p>Toutes les méthodes filtrent implicitement {@code deleted_at IS NULL}
 * via le pattern soft-delete des services. Pour la lecture brute (admin),
 * utiliser {@link JpaRepository#findById(Object)} qui retourne aussi les rows
 * supprimées (à filtrer manuellement).
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** Lookup par email (login). Insensible à la casse. */
    Optional<User> findByEmailIgnoreCase(String email);

    /** Lookup par téléphone (login OTP, recherche client par phone). */
    Optional<User> findByPhone(String phone);

    /** Lookup par code de parrainage — résolution code ami (useCareChat / useAIAssistant). */
    Optional<User> findByReferralCode(String referralCode);

    /** Batch lookup par liste d'IDs — anti N+1 (useFriendships, useTeamMembers, useSupportTickets). */
    @Query("SELECT u FROM User u WHERE u.id IN :ids AND u.deletedAt IS NULL")
    java.util.List<User> findAllByIds(@Param("ids") java.util.Collection<UUID> ids);

    /** Existence rapide par email (signup uniqueness check). */
    boolean existsByEmailIgnoreCase(String email);

    /** Existence rapide par téléphone. */
    boolean existsByPhone(String phone);

    /**
     * Liste paginée des users d'un rôle (code), avec filtrage optionnel par tenant.
     *
     * <p>Exclut les rows soft-deleted ({@code deleted_at IS NULL}).
     * Si {@code tenantId} est null, ramène tous les tenants (utile pour les admins
     * cross-tenant). Sinon, filtre strictement.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.role.code = :roleCode
          AND u.deletedAt IS NULL
          AND (:tenantId IS NULL OR u.tenantId = :tenantId)
        """)
    Page<User> findByRoleCode(
        @Param("roleCode") String roleCode,
        @Param("tenantId") UUID tenantId,
        Pageable pageable
    );
}
