package com.onesley.oneclick.modules.system;

import com.onesley.oneclick.modules.system.api.SystemDtos.*;
import com.onesley.oneclick.modules.system.internal.QuotaChangeLog;
import com.onesley.oneclick.modules.system.internal.SystemHealthCheck;
import com.onesley.oneclick.modules.system.internal.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/system")
@Tag(name = "System monitoring", description = "Sprint H — health checks, alerts, quota change logs (admin)")
public class SystemController {

    private final SystemService service;

    public SystemController(SystemService service) {
        this.service = service;
    }

    // ─── Health checks ────────────────────────────────────────────────────
    @GetMapping("/health-checks")
    @Operation(summary = "Heartbeats jobs (admin)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public List<HealthCheckDto> findRecentHealthChecks(@RequestParam(defaultValue = "50") int limit) {
        return service.findRecentHealthChecks(limit);
    }

    @GetMapping("/health-checks/by-component/{component}")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public List<HealthCheckDto> findByComponent(@PathVariable String component, @RequestParam(defaultValue = "10") int limit) {
        return service.findByComponent(component, limit);
    }

    public record HealthCheckRecordDto(
        @NotNull String component, @NotNull String status,
        Integer latencyMs, String errorMessage, Map<String, Object> metadata
    ) {}

    @PostMapping("/health-checks")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public ResponseEntity<HealthCheckDto> recordHealthCheck(@RequestBody HealthCheckRecordDto dto) {
        SystemHealthCheck h = new SystemHealthCheck();
        h.setComponent(dto.component());
        h.setStatus(dto.status());
        h.setLatencyMs(dto.latencyMs());
        h.setErrorMessage(dto.errorMessage());
        h.setMetadata(dto.metadata());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordHealthCheck(h));
    }

    // ─── Alerts ───────────────────────────────────────────────────────────
    @GetMapping("/alerts")
    @Operation(summary = "Alertes (filter unacknowledged optionnel)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public List<AlertDto> findAlerts(
        @RequestParam(required = false) Boolean unacknowledgedOnly,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return Boolean.TRUE.equals(unacknowledgedOnly) ? service.findUnacknowledged() : service.findRecentAlerts(limit);
    }

    @PostMapping("/alerts/{id}/acknowledge")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public AlertDto acknowledge(@PathVariable UUID id, @RequestParam UUID userId) {
        return service.acknowledge(id, userId);
    }

    @GetMapping("/alert-rules")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public List<AlertRuleDto> findRules() {
        return service.findRules();
    }

    // ─── Quota change logs ────────────────────────────────────────────────
    @GetMapping("/quota-change-logs")
    @Operation(summary = "Audit changements de quotas (admin)")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN')")
    public List<QuotaChangeLogDto> findRecentQuotaLogs(
        @RequestParam(required = false) UUID restaurantId,
        @RequestParam(defaultValue = "200") int limit
    ) {
        return restaurantId != null
            ? service.findQuotaLogsByRestaurant(restaurantId)
            : service.findRecentQuotaLogs(limit);
    }

    public record QuotaChangeRecordDto(
        UUID tenantId, UUID restaurantId, UUID userId,
        @NotNull String quotaType, Integer oldValue, Integer newValue, String reason
    ) {}

    @PostMapping("/quota-change-logs")
    @PreAuthorize("hasAnyRole('SUPERADMIN','GROUP_ADMIN','RESTAURATEUR')")
    public ResponseEntity<QuotaChangeLogDto> recordQuotaChange(@RequestBody QuotaChangeRecordDto dto) {
        QuotaChangeLog q = new QuotaChangeLog();
        q.setTenantId(dto.tenantId());
        q.setRestaurantId(dto.restaurantId());
        q.setUserId(dto.userId());
        q.setQuotaType(dto.quotaType());
        q.setOldValue(dto.oldValue());
        q.setNewValue(dto.newValue());
        q.setReason(dto.reason());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordQuotaChange(q));
    }
}
