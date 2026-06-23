package com.onesley.oneclick.modules.restaurant_referral.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository {@link RestaurantReferral} — CRUD + finders dérivés + read-views native SQL pour le
 * scope cross-module (tenant du resto + owner-check) sans dépendre des modules business
 * {@code restaurant}/{@code loyalty} (invariant Modulith « 0 dépendance business↔business » —
 * même pattern que {@code PccFeedbackRepository}).
 */
@Repository
public interface RestaurantReferralRepository extends JpaRepository<RestaurantReferral, UUID> {

    /** Code de parrainage existant pour ce resto parrain (1 code par resto). */
    Optional<RestaurantReferral> findByReferrerRestaurantId(UUID referrerRestaurantId);

    /** Résolution d'un parrainage par son code (insensible à la casse). */
    Optional<RestaurantReferral> findByReferralCodeIgnoreCase(String referralCode);

    /** Existe-t-il déjà un code pour ce resto parrain ? (génération idempotente). */
    boolean existsByReferrerRestaurantId(UUID referrerRestaurantId);

    /** Le resto FILLEUL a-t-il déjà été parrainé ? (anti double-activation). */
    boolean existsByRefereeRestaurantId(UUID refereeRestaurantId);

    /** Parrainages ACTIVÉS portés par ce resto parrain (liste owner). */
    List<RestaurantReferral> findAllByReferrerRestaurantIdAndStatus(UUID referrerRestaurantId, String status);

    // ─── Read-views native SQL (scope cross-module, pas d'import module business) ─────────────

    /**
     * tenant_id du resto (non soft-deleted). Sert au scope mono-tenant « oneclick » sans importer
     * le module {@code restaurant}. Vide si resto inconnu/supprimé.
     */
    @Query(value = """
        SELECT r.tenant_id
        FROM restaurants r
        WHERE r.id = :restaurantId AND r.deleted_at IS NULL
        """, nativeQuery = true)
    Optional<UUID> findTenantIdOfRestaurant(@Param("restaurantId") UUID restaurantId);

    /**
     * true si {@code userId} est OWNER ACTIF du resto ({@code restaurant_staffs.role_code='owner'}
     * non supprimé). ABAC owner-scope sans importer le module {@code restaurant}.
     */
    @Query(value = """
        SELECT EXISTS (
            SELECT 1 FROM restaurant_staffs rs
            WHERE rs.user_id = :userId
              AND rs.restaurant_id = :restaurantId
              AND rs.role_code = 'owner'
              AND rs.deleted_at IS NULL
        )
        """, nativeQuery = true)
    boolean isOwnerOfRestaurant(@Param("userId") UUID userId, @Param("restaurantId") UUID restaurantId);

    /**
     * user_id de l'OWNER ACTIF du resto ({@code restaurant_staffs.role_code='owner'} non supprimé) —
     * sert au cas admin (qui agit au nom du resto) pour porter la récompense sur le vrai parrain.
     * {@code LIMIT 1} : 1 owner attendu par resto (cf. seed). Vide si aucun owner actif.
     */
    @Query(value = """
        SELECT rs.user_id
        FROM restaurant_staffs rs
        WHERE rs.restaurant_id = :restaurantId
          AND rs.role_code = 'owner'
          AND rs.deleted_at IS NULL
        LIMIT 1
        """, nativeQuery = true)
    Optional<UUID> findOwnerUserIdOf(@Param("restaurantId") UUID restaurantId);
}
