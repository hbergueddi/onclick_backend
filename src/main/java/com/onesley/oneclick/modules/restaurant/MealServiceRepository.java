package com.onesley.oneclick.modules.restaurant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MealServiceRepository extends JpaRepository<MealService, UUID>, JpaSpecificationExecutor<MealService> {
    List<MealService> findAllByRestaurantId(UUID restaurantId);
}
