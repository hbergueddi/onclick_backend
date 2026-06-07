package com.onesley.oneclick.modules.loyalty.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository {@link GainRule} — règles loyalty par-restaurant.
 *
 * <p>Soft delete : filtrer {@code WHERE deleted_at IS NULL} via finders dédiés.
 * Les méthodes JpaRepository standard ne filtrent pas — utilisation à éviter
 * en dehors d'opérations admin / cleanup.
 */
@Repository
public interface GainRuleRepository extends JpaRepository<GainRule, UUID>, JpaSpecificationExecutor<GainRule> {

    Optional<GainRule> findByRestaurantIdAndDeletedAtIsNull(UUID restaurantId);

    List<GainRule> findAllByDeletedAtIsNull();

    Optional<GainRule> findByIdAndDeletedAtIsNull(UUID id);

    // ─── Gap #1 — assignation en masse d'une tier-rule (FORGE) ────────────────

    /** Toutes les gain_rules assignées depuis une tier-rule plateforme donnée. */
    List<GainRule> findBySourceTierRuleIdAndDeletedAtIsNull(UUID sourceTierRuleId);

    /** Sous-ensemble restreint à une liste de restaurants (unassign ciblé). */
    List<GainRule> findBySourceTierRuleIdAndRestaurantIdInAndDeletedAtIsNull(
        UUID sourceTierRuleId, List<UUID> restaurantIds);

    /**
     * Comptes d'assignation par tier-rule source (badge "X restos" de la FORGE).
     * Retourne des paires {@code [sourceTierRuleId (UUID), count (Long)]}.
     */
    @Query("""
        SELECT g.sourceTierRuleId, COUNT(g)
        FROM GainRule g
        WHERE g.sourceTierRuleId IS NOT NULL AND g.deletedAt IS NULL
        GROUP BY g.sourceTierRuleId
        """)
    List<Object[]> countAssignmentsBySourceTierRule();
}
