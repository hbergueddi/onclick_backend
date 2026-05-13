package com.onesley.oneclick.modules.system.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SystemAlertRepository extends JpaRepository<SystemAlert, UUID> {

    @Query("SELECT a FROM SystemAlert a WHERE a.acknowledgedAt IS NULL ORDER BY a.createdAt DESC")
    List<SystemAlert> findUnacknowledged();

    @Query("SELECT a FROM SystemAlert a ORDER BY a.createdAt DESC")
    List<SystemAlert> findRecent(org.springframework.data.domain.Pageable pageable);
}
