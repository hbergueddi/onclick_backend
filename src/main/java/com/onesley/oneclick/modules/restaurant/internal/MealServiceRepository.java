package com.onesley.oneclick.modules.restaurant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MealServiceRepository extends JpaRepository<MealService, UUID>, JpaSpecificationExecutor<MealService> {
    List<MealService> findAllByRestaurantId(UUID restaurantId);

    /**
     * Vue admin consolidée — tous les créneaux actifs, enrichis du restaurant
     * (nom/ville) et du groupe propriétaire. Alimente l'onglet "Quotas Click&Go".
     * Native (JOIN restaurants + LEFT JOIN restaurant_groups) — projection
     * {@link MealServiceOverviewView}.
     */
    @Query(value = """
        SELECT
            s.id            AS id,
            s.restaurant_id AS restaurantId,
            r.name          AS restaurantName,
            r.city          AS restaurantCity,
            g.name          AS groupName,
            s.type          AS type,
            s.name          AS name,
            s.clickgo_quota AS clickgoQuota,
            s.capacite_max  AS capaciteMax,
            s.status        AS status,
            s.start_time    AS startTime,
            s.end_time      AS endTime
        FROM restaurant_services s
        JOIN restaurants r ON r.id = s.restaurant_id
        LEFT JOIN restaurant_groups g ON g.id = r.group_id
        WHERE s.status = 'actif' AND r.deleted_at IS NULL
        ORDER BY r.name, s.start_time
        """, nativeQuery = true)
    List<MealServiceOverviewView> findAllOverview();
}
