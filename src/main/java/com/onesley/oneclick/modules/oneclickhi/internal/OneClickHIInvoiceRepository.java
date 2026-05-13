package com.onesley.oneclick.modules.oneclickhi.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OneClickHIInvoiceRepository extends JpaRepository<OneClickHIInvoice, UUID> {

    @Query("SELECT i FROM OneClickHIInvoice i WHERE i.deletedAt IS NULL ORDER BY i.periodMonth DESC, i.createdAt DESC")
    List<OneClickHIInvoice> findAllActive();

    @Query("SELECT i FROM OneClickHIInvoice i WHERE i.restaurantId = :restaurantId AND i.deletedAt IS NULL ORDER BY i.periodMonth DESC")
    List<OneClickHIInvoice> findByRestaurant(UUID restaurantId);

    @Query("SELECT i FROM OneClickHIInvoice i WHERE i.tenantId = :tenantId AND i.deletedAt IS NULL ORDER BY i.periodMonth DESC")
    List<OneClickHIInvoice> findByTenant(UUID tenantId);
}
