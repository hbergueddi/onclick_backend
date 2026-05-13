package com.onesley.oneclick.modules.system.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface QuotaChangeLogRepository extends JpaRepository<QuotaChangeLog, UUID> {

    @Query("SELECT q FROM QuotaChangeLog q WHERE q.restaurantId = :restaurantId ORDER BY q.createdAt DESC")
    List<QuotaChangeLog> findByRestaurant(UUID restaurantId);

    @Query("SELECT q FROM QuotaChangeLog q ORDER BY q.createdAt DESC")
    List<QuotaChangeLog> findRecent(org.springframework.data.domain.Pageable pageable);
}
