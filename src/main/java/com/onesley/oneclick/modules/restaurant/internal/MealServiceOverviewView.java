package com.onesley.oneclick.modules.restaurant.internal;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Projection Spring Data — créneau de service enrichi du restaurant + groupe,
 * pour la vue admin consolidée /reservations · onglet "Quotas Click&Go".
 *
 * <p>Lecture via {@code nativeQuery} (JOIN restaurants + restaurant_groups) dans
 * {@link MealServiceRepository#findAllOverview}. Binding par alias SQL
 * (snake_case → camelCase via convention Spring Data).</p>
 */
public interface MealServiceOverviewView {

    UUID getId();
    UUID getRestaurantId();
    String getRestaurantName();
    String getRestaurantCity();
    String getGroupName();
    String getType();
    String getName();
    Integer getClickgoQuota();
    Integer getCapaciteMax();
    String getStatus();
    LocalTime getStartTime();
    LocalTime getEndTime();
}
