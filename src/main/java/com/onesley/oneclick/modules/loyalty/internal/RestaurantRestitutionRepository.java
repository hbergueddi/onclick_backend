package com.onesley.oneclick.modules.loyalty.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RestaurantRestitutionRepository extends JpaRepository<RestaurantRestitution, UUID> {

    @Query("SELECT r FROM RestaurantRestitution r WHERE r.restaurantId = :restaurantId ORDER BY r.createdAt DESC")
    List<RestaurantRestitution> findByRestaurant(UUID restaurantId);

    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM RestaurantRestitution r WHERE r.restaurantId = :restaurantId AND r.status = 'paid'")
    java.math.BigDecimal sumPaidAmount(UUID restaurantId);
}
