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

    /** Lookup par code de parrainage insensible à la casse (Gap #8 — activation par code saisi). */
    Optional<User> findByReferralCodeIgnoreCase(String referralCode);

    /** Batch lookup par liste d'IDs — anti N+1 (useFriendships, useTeamMembers, useSupportTickets). */
    @Query("SELECT u FROM User u WHERE u.id IN :ids AND u.deletedAt IS NULL")
    java.util.List<User> findAllByIds(@Param("ids") java.util.Collection<UUID> ids);

    /** Ids des utilisateurs actifs d'un rôle donné (ex: {@code SUPERADMIN}) — destinataires d'alertes globales. */
    @Query("SELECT u.id FROM User u WHERE u.role.code = :roleCode AND u.deletedAt IS NULL")
    java.util.List<UUID> findIdsByRoleCode(@Param("roleCode") String roleCode);

    /**
     * Résolution « Ma Famille » (PCC Lot 5) — email scopé tenant, insensible à la casse,
     * soft-deletes exclus. Le filtre {@code tenant_id} garantit qu'on ne résout qu'un membre
     * du tenant du caller (générique, pas de tenant en dur).
     */
    @Query("""
        SELECT u FROM User u
        WHERE LOWER(u.email) = LOWER(:email)
          AND u.tenantId = :tenantId
          AND u.deletedAt IS NULL
        """)
    Optional<User> findByEmailIgnoreCaseAndTenant(@Param("email") String email, @Param("tenantId") UUID tenantId);

    /**
     * Résolution « Ma Famille » — code de parrainage scopé tenant (comparé en majuscules),
     * soft-deletes exclus.
     */
    @Query("""
        SELECT u FROM User u
        WHERE UPPER(u.referralCode) = UPPER(:code)
          AND u.tenantId = :tenantId
          AND u.deletedAt IS NULL
        """)
    Optional<User> findByReferralCodeIgnoreCaseAndTenant(@Param("code") String code, @Param("tenantId") UUID tenantId);

    /**
     * Résolution « Ma Famille » — téléphone scopé tenant, normalisé (espaces / tirets /
     * points retirés des 2 côtés, exactement comme le legacy RPC). Native query : la
     * normalisation par {@code REPLACE} imbriqués n'est pas exprimable en JPQL portable.
     * {@code LIMIT 1} : si 2 membres partagent un numéro normalisé (improbable, phone UNIQUE),
     * on en prend un déterministe par id.
     */
    @Query(value = """
        SELECT * FROM users u
        WHERE REPLACE(REPLACE(REPLACE(u.phone, ' ', ''), '-', ''), '.', '')
            = REPLACE(REPLACE(REPLACE(:phone, ' ', ''), '-', ''), '.', '')
          AND u.tenant_id = :tenantId
          AND u.deleted_at IS NULL
        ORDER BY u.id
        LIMIT 1
        """, nativeQuery = true)
    Optional<User> findByNormalizedPhoneAndTenant(@Param("phone") String phone, @Param("tenantId") UUID tenantId);

    /** Existence rapide par email (signup uniqueness check). */
    boolean existsByEmailIgnoreCase(String email);

    /** Existence rapide par téléphone. */
    boolean existsByPhone(String phone);

    /**
     * Comptes restés en {@code pending_email_verification} et créés avant {@code cutoff}
     * (signup abandonné ou bot — l'OTP email n'a jamais été validé). Purgés par
     * {@code PendingSignupPurgeJob} (cron horaire) pour libérer l'email + éviter le bloat DB.
     * Exclut les soft-deletes ({@code deleted_at IS NULL}).
     */
    @Query("SELECT u FROM User u WHERE u.status = :status AND u.createdAt < :cutoff AND u.deletedAt IS NULL")
    java.util.List<User> findStalePendingVerification(
        @Param("status") String status, @Param("cutoff") java.time.Instant cutoff);

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

    /**
     * Recherche floue de clients (rôle CLIENT) par téléphone / prénom / nom — usage
     * staff Snap2Earn (identification du porteur du ticket). Insensible à la casse,
     * limité via {@link Pageable}. Soft-deletes exclus. Scoping autorité/ABAC porté
     * par le contrôleur ({@code CREATE:LOYALTY} + RestaurantAccessGuard).
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.role.code = 'CLIENT'
          AND u.deletedAt IS NULL
          AND (
            LOWER(COALESCE(u.phone, '')) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
            OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%'))
          )
        ORDER BY u.firstName, u.lastName
        """)
    java.util.List<User> searchClients(@Param("q") String q, Pageable pageable);

    /**
     * Bug 34 (UserDetails) — Eager load user + role + permissions + menu + action
     * en 1 query pour {@code OneClickUserDetailsService} (évite N+1 + alimente le
     * cache Redis "userDetails", 1h TTL).
     *
     * <p>{@code LEFT JOIN FETCH} multi-niveau pour ne pas exclure les users sans
     * permissions (ex: rôle CLIENT n'a pas de permissions seedées au moment de
     * l'auth). Le {@code WHERE u.deletedAt IS NULL} préserve la convention
     * soft-delete partagée par tous les finders métier.
     *
     * <p>Coût attendu : 1 SELECT (1 hit Postgres) au lieu de 4 (user + role +
     * permissions + menu/action) — gain ×3 sur la latence d'auth quand le cache
     * Redis miss.
     */
    @Query("""
        SELECT u FROM User u
        LEFT JOIN FETCH u.role r
        LEFT JOIN FETCH r.permissions p
        LEFT JOIN FETCH p.menu
        LEFT JOIN FETCH p.action
        WHERE u.id = :id AND u.deletedAt IS NULL
        """)
    Optional<User> findByIdWithRoleAndPermissions(@Param("id") UUID id);
}
