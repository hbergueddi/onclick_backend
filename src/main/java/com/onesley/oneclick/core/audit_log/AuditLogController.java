package com.onesley.oneclick.core.audit_log;

import com.onesley.oneclick.shared.PageResponse;
import com.onesley.oneclick.core.audit_log.internal.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.onesley.oneclick.core.audit_log.api.AuditLogDtos.*;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.AuditLogCreateDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.AuditLogDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.ErrorLogCreateDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.ErrorLogDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.JobExecutionDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.SystemEventCreateDto;
import com.onesley.oneclick.core.audit_log.api.AuditLogDtos.SystemEventDto;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "AuditLog", description = "Journal audit + events système + erreurs + jobs (§15)")
public class AuditLogController {

    private final AuditLogService service;

    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    // ─── AuditLog ────────────────────────────────────────────────────────────

    @GetMapping("/logs")
    @Operation(summary = "Audit logs paginés — filtres userId / tenantId / entityType / entityId")
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public PageResponse<AuditLogDto> findAuditLogs(
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) UUID tenantId,
        @RequestParam(required = false) String entityType,
        @RequestParam(required = false) UUID entityId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return PageResponse.from(service.findAuditLogs(userId, tenantId, entityType, entityId, page, size));
    }

    @PostMapping("/logs")
    @Operation(summary = "Enregistre un audit log (généralement appelé par les services internes)")
    @PreAuthorize("hasAuthority('CREATE:AUDIT')")
    public ResponseEntity<AuditLogDto> recordAudit(@Valid @RequestBody AuditLogCreateDto dto) {
        AuditLogDto a = service.recordAudit(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(a);
    }

    // ─── System events ───────────────────────────────────────────────────────

    @GetMapping("/events")
    @Operation(summary = "Events système paginés — filtres type / unprocessedOnly")
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public PageResponse<SystemEventDto> findEvents(
        @RequestParam(required = false) String type,
        @RequestParam(required = false) Boolean unprocessedOnly,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return PageResponse.from(service.findEvents(type, unprocessedOnly, page, size));
    }

    @PostMapping("/events")
    @Operation(summary = "Publie un event système (sera traité de façon asynchrone)")
    @PreAuthorize("hasAuthority('CREATE:AUDIT')")
    public ResponseEntity<SystemEventDto> publishEvent(@Valid @RequestBody SystemEventCreateDto dto) {
        SystemEventDto e = service.publishEvent(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(e);
    }

    // ─── Errors ──────────────────────────────────────────────────────────────

    @GetMapping("/errors")
    @Operation(summary = "Logs d'erreurs paginés — filtres serviceName / severity")
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public PageResponse<ErrorLogDto> findErrors(
        @RequestParam(required = false) String serviceName,
        @RequestParam(required = false) String severity,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return PageResponse.from(service.findErrors(serviceName, severity, page, size));
    }

    @PostMapping("/errors")
    @Operation(summary = "Enregistre une erreur (frontend Sentry-like ou intégration externe)")
    @PreAuthorize("hasAuthority('CREATE:AUDIT')")
    public ResponseEntity<ErrorLogDto> recordError(@Valid @RequestBody ErrorLogCreateDto dto) {
        ErrorLogDto e = service.recordError(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(e);
    }

    // ─── Jobs (read-only) ────────────────────────────────────────────────────

    @GetMapping("/jobs")
    @Operation(summary = "Historique d'exécutions de jobs — filtres jobName / status")
    @PreAuthorize("hasAuthority('VIEW:AUDIT')")
    public PageResponse<JobExecutionDto> findJobs(
        @RequestParam(required = false) String jobName,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return PageResponse.from(service.findJobs(jobName, status, page, size));
    }
}
