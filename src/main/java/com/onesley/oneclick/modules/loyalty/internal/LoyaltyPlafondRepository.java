package com.onesley.oneclick.modules.loyalty.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository {@link LoyaltyPlafond} — plafonds / limites Lounge.
 * Soft delete : finders dédiés filtrent {@code WHERE deleted_at IS NULL}.
 */
@Repository
public interface LoyaltyPlafondRepository extends JpaRepository<LoyaltyPlafond, UUID> {

    List<LoyaltyPlafond> findAllByDeletedAtIsNullOrderByScopeAscCreatedAtAsc();

    Optional<LoyaltyPlafond> findByIdAndDeletedAtIsNull(UUID id);
}
