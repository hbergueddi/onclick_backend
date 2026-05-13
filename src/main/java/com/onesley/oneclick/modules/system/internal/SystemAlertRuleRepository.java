package com.onesley.oneclick.modules.system.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SystemAlertRuleRepository extends JpaRepository<SystemAlertRule, UUID> {

    @Query("SELECT r FROM SystemAlertRule r WHERE r.enabled = true ORDER BY r.severity ASC")
    List<SystemAlertRule> findAllEnabled();
}
