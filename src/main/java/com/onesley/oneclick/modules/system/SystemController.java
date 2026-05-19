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

/**
 * Bug 32 (Batch D RBAC v2) — RBAC v2 senior strict hasAuthority('VERB:AUDIT')
 */
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
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public List<HealthCheckDto> findRecentHealthChecks(@RequestParam(defaultValue = "50") int limit) {
        return service.findRecentHealthChecks(limit);
    }

    @GetMapping("/health-checks/by-component/{component}")
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public List<HealthCheckDto> findByComponent(@PathVariable String component, @RequestParam(defaultValue = "10") int limit) {
        return service.findByComponent(component, limit);
    }

    public record HealthCheckRecordDto(
        @NotNull String component, @NotNull String status,
        Integer latencyMs, String errorMessage, Map<String, Object> metadata
    ) {}

    @PostMapping("/health-checks")
    @PreAuthorize("hasAuthority('CREATE:AUDIT')")
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
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public List<AlertDto> findAlerts(
        @RequestParam(required = false) Boolean unacknowledgedOnly,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return Boolean.TRUE.equals(unacknowledgedOnly) ? service.findUnacknowledged() : service.findRecentAlerts(limit);
    }

    @PostMapping("/alerts/{id}/acknowledge")
    @PreAuthorize("hasAuthority('UPDATE:AUDIT')")
    public AlertDto acknowledge(@PathVariable UUID id, @RequestParam UUID userId) {
        return service.acknowledge(id, userId);
    }

    @GetMapping("/alert-rules")
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public List<AlertRuleDto> findRules() {
        return service.findRules();
    }

    // ─── Quota change logs ────────────────────────────────────────────────
    @GetMapping("/quota-change-logs")
    @Operation(summary = "Audit changements de quotas (admin)")
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
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
    @PreAuthorize("hasAuthority('CREATE:AUDIT')")
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

    // ─── V24 — Sprint K : documentation interne (DocumentExport) ────────────

    @GetMapping("/documents/{id}")
    @Operation(summary = "Document interne courant (admin)")
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public AppDocumentDto findDocument(@PathVariable String id) {
        return service.findDocument(id);
    }

    @PutMapping("/documents/{id}")
    @Operation(summary = "Upsert du document interne courant (admin)")
    @PreAuthorize("hasAuthority('UPDATE:AUDIT')")
    public AppDocumentDto upsertDocument(@PathVariable String id, @RequestBody AppDocumentUpsertDto dto) {
        return service.upsertDocument(id, dto);
    }

    @GetMapping("/documents/{id}/versions")
    @Operation(summary = "Historique des révisions d'un document (admin)")
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public List<DocumentVersionDto> findDocumentVersions(@PathVariable String id) {
        return service.findDocumentVersions(id);
    }

    @PostMapping("/documents/{id}/versions")
    @Operation(summary = "Archive une révision d'un document (admin)")
    @PreAuthorize("hasAuthority('CREATE:AUDIT')")
    public ResponseEntity<DocumentVersionDto> addDocumentVersion(
        @PathVariable String id, @RequestBody @jakarta.validation.Valid DocumentVersionCreateDto dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addDocumentVersion(id, dto));
    }

    // ─── V24 — Sprint K : rôles personnalisés admin (GestionRoles) ──────────

    @GetMapping("/custom-roles")
    @Operation(summary = "Liste des rôles personnalisés (admin)")
    @PreAuthorize("hasAuthority('VIEW:USERS')")
    public List<CustomRoleDto> findCustomRoles() {
        return service.findCustomRoles();
    }

    @PostMapping("/custom-roles")
    @Operation(summary = "Crée un rôle personnalisé (admin)")
    @PreAuthorize("hasAuthority('CREATE:USERS')")
    public ResponseEntity<CustomRoleDto> createCustomRole(@RequestBody @jakarta.validation.Valid CustomRoleCreateDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createCustomRole(dto));
    }

    @DeleteMapping("/custom-roles/{id}")
    @Operation(summary = "Supprime un rôle personnalisé (admin)")
    @PreAuthorize("hasAuthority('DELETE:USERS')")
    public ResponseEntity<Void> deleteCustomRole(@PathVariable UUID id) {
        service.deleteCustomRole(id);
        return ResponseEntity.noContent().build();
    }
}
