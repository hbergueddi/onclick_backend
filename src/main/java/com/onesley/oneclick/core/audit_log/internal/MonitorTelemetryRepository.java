package com.onesley.oneclick.core.audit_log.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MonitorTelemetryRepository extends JpaRepository<MonitorTelemetry, UUID> {

    @Query("SELECT m FROM MonitorTelemetry m ORDER BY m.createdAt DESC")
    List<MonitorTelemetry> findRecent(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT m FROM MonitorTelemetry m WHERE m.eventType = :eventType ORDER BY m.createdAt DESC")
    List<MonitorTelemetry> findByEventType(String eventType, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT m FROM MonitorTelemetry m WHERE m.userId = :userId ORDER BY m.createdAt DESC")
    List<MonitorTelemetry> findByUserId(UUID userId, org.springframework.data.domain.Pageable pageable);
}
