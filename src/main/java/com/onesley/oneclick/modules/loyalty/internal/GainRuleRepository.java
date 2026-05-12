package com.onesley.oneclick.modules.loyalty.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
}
