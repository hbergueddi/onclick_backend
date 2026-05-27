package com.onesley.oneclick.modules.loyalty.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository {@link TierRestaurantOffer} — offres fidélité par niveau & resto. */
@Repository
public interface TierRestaurantOfferRepository extends JpaRepository<TierRestaurantOffer, UUID> {

    List<TierRestaurantOffer> findAllByDeletedAtIsNullOrderByTierNameAsc();

    List<TierRestaurantOffer> findByRestaurantIdAndDeletedAtIsNullOrderByTierNameAsc(UUID restaurantId);

    Optional<TierRestaurantOffer> findByIdAndDeletedAtIsNull(UUID id);
}
