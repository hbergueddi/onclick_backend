package com.onesley.oneclick.modules.restaurant.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ExploreFeaturedRepository extends JpaRepository<ExploreFeatured, UUID> {

    @Query("SELECT f FROM ExploreFeatured f WHERE f.enabled = true ORDER BY f.rank ASC")
    List<ExploreFeatured> findAllEnabledOrdered();

    /**
     * Variante scopée tenant (fuite de périmètre) : featured activés dont le <b>restaurant</b>
     * appartient à un tenant visible par le caller. Join explicite ExploreFeatured→Restaurant (même
     * module) pour s'appuyer sur {@code restaurants.tenant_id} (toujours renseigné).
     */
    @Query("SELECT f FROM ExploreFeatured f JOIN Restaurant r ON r.id = f.restaurantId "
            + "WHERE f.enabled = true AND r.deletedAt IS NULL AND r.tenantId IN :tenantIds "
            + "ORDER BY f.rank ASC")
    List<ExploreFeatured> findAllEnabledOrderedForTenants(java.util.Collection<UUID> tenantIds);

    /** Liste admin : tous les featured (activés ET désactivés), triés par rang. */
    List<ExploreFeatured> findAllByOrderByRankAsc();

    @Query("SELECT f FROM ExploreFeatured f WHERE f.restaurantId = :restaurantId")
    java.util.Optional<ExploreFeatured> findByRestaurant(UUID restaurantId);
}
