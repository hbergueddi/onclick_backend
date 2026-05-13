package com.onesley.oneclick.core.notification.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PromoNotificationRequestRepository extends JpaRepository<PromoNotificationRequest, UUID> {

    @Query("SELECT p FROM PromoNotificationRequest p WHERE p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<PromoNotificationRequest> findAllActive();

    @Query("SELECT p FROM PromoNotificationRequest p WHERE p.status = :status AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<PromoNotificationRequest> findByStatus(String status);

    @Query("SELECT p FROM PromoNotificationRequest p WHERE p.restaurantId = :restaurantId AND p.deletedAt IS NULL ORDER BY p.createdAt DESC")
    List<PromoNotificationRequest> findByRestaurant(UUID restaurantId);
}
