package com.onesley.oneclick.modules.restaurant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ExploreFeaturedRepository extends JpaRepository<ExploreFeatured, UUID> {

    @Query("SELECT f FROM ExploreFeatured f WHERE f.enabled = true ORDER BY f.rank ASC")
    List<ExploreFeatured> findAllEnabledOrdered();

    @Query("SELECT f FROM ExploreFeatured f WHERE f.restaurantId = :restaurantId")
    java.util.Optional<ExploreFeatured> findByRestaurant(UUID restaurantId);
}
