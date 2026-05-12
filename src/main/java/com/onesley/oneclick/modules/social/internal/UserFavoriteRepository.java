package com.onesley.oneclick.modules.social.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository {@link UserFavorite} — accès CRUD + finders dérivés.
 *
 * <p>Pas de soft delete sur {@code user_favorites} : DELETE physique (UNIQUE
 * {@code (user_id, restaurant_id)} permet de re-favoriser).
 */
@Repository
public interface UserFavoriteRepository extends JpaRepository<UserFavorite, UUID>, JpaSpecificationExecutor<UserFavorite> {

    List<UserFavorite> findAllByUserId(UUID userId);

    boolean existsByUserIdAndRestaurantId(UUID userId, UUID restaurantId);

    @Modifying
    @Query("DELETE FROM UserFavorite f WHERE f.userId = :userId AND f.restaurantId = :restaurantId")
    int deleteByUserIdAndRestaurantId(@Param("userId") UUID userId, @Param("restaurantId") UUID restaurantId);
}
