package com.onesley.oneclick.modules.loyalty.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link LoyaltyRule} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface LoyaltyRuleRepository extends JpaRepository<LoyaltyRule, UUID>, JpaSpecificationExecutor<LoyaltyRule> {
    java.util.List<LoyaltyRule> findAllByRestaurantId(java.util.UUID restaurantId);

    /**
     * Règle active de valeur du point d'un restaurant — source de vérité de
     * {@code point_value} (1 pt = N MAD). On ne filtre que sur des propriétés
     * <b>mappées</b> par l'entité ({@code enabled}, {@code restaurantId},
     * {@code createdAt}) : {@code loyalty_rules.deleted_at} existe en DB (V12)
     * mais n'est pas modélisé sur {@link LoyaltyRule} (extends TimestampedEntity,
     * pas SoftDeletableAuditedEntity), donc inutilisable en JPQL ici.
     *
     * <p>Aucune contrainte UNIQUE sur {@code restaurant_id} → on prend la plus
     * récente (ordre déterministe) pour rester idempotent si plusieurs lignes
     * coexistent.
     */
    java.util.Optional<LoyaltyRule> findFirstByRestaurantIdAndEnabledTrueOrderByCreatedAtDesc(
        java.util.UUID restaurantId);
}
