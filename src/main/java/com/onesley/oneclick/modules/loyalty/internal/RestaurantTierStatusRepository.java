package com.onesley.oneclick.modules.loyalty.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RestaurantTierStatusRepository extends JpaRepository<RestaurantTierStatus, UUID> {
    Optional<RestaurantTierStatus> findByRestaurantId(UUID restaurantId);
}
