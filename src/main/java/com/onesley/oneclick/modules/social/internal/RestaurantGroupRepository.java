package com.onesley.oneclick.modules.social.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RestaurantGroupRepository extends JpaRepository<RestaurantGroup, UUID> {

    @Query("SELECT g FROM RestaurantGroup g WHERE g.deletedAt IS NULL ORDER BY g.createdAt DESC")
    List<RestaurantGroup> findAllActive();

    @Query("SELECT g FROM RestaurantGroup g WHERE g.ownerId = :ownerId AND g.deletedAt IS NULL ORDER BY g.createdAt DESC")
    List<RestaurantGroup> findByOwner(UUID ownerId);
}
