package com.onesley.oneclick.modules.system.internal;

import com.onesley.oneclick.modules.system.api.SystemDtos.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SystemService {

    private final SystemHealthCheckRepository healthRepo;
    private final SystemAlertRepository alertRepo;
    private final SystemAlertRuleRepository ruleRepo;
    private final QuotaChangeLogRepository quotaRepo;

    public SystemService(
        SystemHealthCheckRepository healthRepo,
        SystemAlertRepository alertRepo,
        SystemAlertRuleRepository ruleRepo,
        QuotaChangeLogRepository quotaRepo
    ) {
        this.healthRepo = healthRepo;
        this.alertRepo = alertRepo;
        this.ruleRepo = ruleRepo;
        this.quotaRepo = quotaRepo;
    }

    @Transactional(readOnly = true)
    public List<HealthCheckDto> findRecentHealthChecks(int limit) {
        return healthRepo.findRecent(PageRequest.of(0, limit)).stream().map(HealthCheckDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<HealthCheckDto> findByComponent(String component, int limit) {
        return healthRepo.findByComponent(component, PageRequest.of(0, limit)).stream().map(HealthCheckDto::from).toList();
    }

    public HealthCheckDto recordHealthCheck(SystemHealthCheck check) {
        return HealthCheckDto.from(healthRepo.save(check));
    }

    @Transactional(readOnly = true)
    public List<AlertDto> findUnacknowledged() {
        return alertRepo.findUnacknowledged().stream().map(AlertDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AlertDto> findRecentAlerts(int limit) {
        return alertRepo.findRecent(PageRequest.of(0, limit)).stream().map(AlertDto::from).toList();
    }

    public AlertDto acknowledge(UUID id, UUID acknowledgedBy) {
        SystemAlert a = alertRepo.findById(id).orElseThrow();
        a.setAcknowledgedAt(Instant.now());
        a.setAcknowledgedBy(acknowledgedBy);
        return AlertDto.from(alertRepo.save(a));
    }

    @Transactional(readOnly = true)
    public List<AlertRuleDto> findRules() {
        return ruleRepo.findAllEnabled().stream().map(AlertRuleDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<QuotaChangeLogDto> findRecentQuotaLogs(int limit) {
        return quotaRepo.findRecent(PageRequest.of(0, limit)).stream().map(QuotaChangeLogDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<QuotaChangeLogDto> findQuotaLogsByRestaurant(UUID restaurantId) {
        return quotaRepo.findByRestaurant(restaurantId).stream().map(QuotaChangeLogDto::from).toList();
    }

    public QuotaChangeLogDto recordQuotaChange(QuotaChangeLog log) {
        return QuotaChangeLogDto.from(quotaRepo.save(log));
    }
}
