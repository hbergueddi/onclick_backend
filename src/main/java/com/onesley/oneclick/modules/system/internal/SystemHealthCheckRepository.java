package com.onesley.oneclick.modules.system.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SystemHealthCheckRepository extends JpaRepository<SystemHealthCheck, UUID> {

    @Query("SELECT h FROM SystemHealthCheck h ORDER BY h.checkedAt DESC")
    List<SystemHealthCheck> findRecent(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT h FROM SystemHealthCheck h WHERE h.component = :component ORDER BY h.checkedAt DESC")
    List<SystemHealthCheck> findByComponent(String component, org.springframework.data.domain.Pageable pageable);
}
