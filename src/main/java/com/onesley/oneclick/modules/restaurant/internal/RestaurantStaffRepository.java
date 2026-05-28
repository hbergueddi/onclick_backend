package com.onesley.oneclick.modules.restaurant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link RestaurantStaff} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface RestaurantStaffRepository extends JpaRepository<RestaurantStaff, UUID>, JpaSpecificationExecutor<RestaurantStaff> {
    java.util.List<RestaurantStaff> findAllByRestaurantId(java.util.UUID restaurantId);
    java.util.List<RestaurantStaff> findAllByUserId(java.util.UUID userId);

    /** IDs des restaurants ayant au moins un staff actif (dashboard admin "Sans équipe"). */
    @Query("SELECT DISTINCT s.restaurantId FROM RestaurantStaff s WHERE s.deletedAt IS NULL")
    java.util.List<UUID> findDistinctStaffedRestaurantIds();
}
