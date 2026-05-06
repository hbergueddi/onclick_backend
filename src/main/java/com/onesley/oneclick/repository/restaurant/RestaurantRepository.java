package com.onesley.oneclick.repository.restaurant;

import com.onesley.oneclick.entity.restaurant.Restaurant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    /** Liste paginée par ville (utilisé sur la page /pocket/explore). */
    Page<Restaurant> findAllByCity(String city, Pageable pageable);

    /** Tous les restaurants d'un tenant donné (whitelabel multi-tenant). */
    List<Restaurant> findAllByTenantId(UUID tenantId);

    /** Restaurants en statut "actif" — affichables côté client. */
    List<Restaurant> findAllByStatus(String status);

    /** Restaurants liés à un groupe (BestPro, Restopro, etc.). */
    List<Restaurant> findAllByGroupId(UUID groupId);

    long countByCity(String city);
}
