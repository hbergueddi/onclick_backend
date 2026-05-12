package com.onesley.oneclick.modules.restaurant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository {@link RestaurantTable} — accès CRUD + finders dérivés.
 *
 * <p>Soft delete (si applicable) : filtrer {@code WHERE deleted_at IS NULL} dans les
 * services. Les méthodes JpaRepository standard ne filtrent pas — utilisation
 * directe à éviter pour les entités avec soft delete.
 */
@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, UUID>, JpaSpecificationExecutor<RestaurantTable> {
    java.util.List<RestaurantTable> findAllByZoneId(java.util.UUID zoneId);

    /**
     * Toutes les tables d'un restaurant — traverse {@code RestaurantTable.zone.restaurant}.
     * Utilisé par le plan de salle ProDesk.
     */
    @Query("SELECT t FROM RestaurantTable t WHERE t.zone.restaurant.id = :restaurantId")
    java.util.List<RestaurantTable> findAllByRestaurantId(java.util.UUID restaurantId);
}
