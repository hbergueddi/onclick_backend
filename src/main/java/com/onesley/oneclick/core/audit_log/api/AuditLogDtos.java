package com.onesley.oneclick.core.audit_log.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.onesley.oneclick.core.audit_log.internal.AuditLog;
import com.onesley.oneclick.core.audit_log.internal.ErrorLog;
import com.onesley.oneclick.core.audit_log.internal.JobExecution;

public final class AuditLogDtos {

    private AuditLogDtos() {}

    // ─── AuditLog ────────────────────────────────────────────────────────────

    public record AuditLogDto(UUID id, UUID userId, UUID tenantId, String entityType, UUID entityId,
                              String action, Map<String, Object> diff, String ipAddress, String userAgent,
                              Instant createdAt) {
        public static AuditLogDto from(AuditLog a) {
            return new AuditLogDto(a.getId(), a.getUserId(), a.getTenantId(), a.getEntityType(),
                a.getEntityId(), a.getAction(), a.getDiff(), a.getIpAddress(), a.getUserAgent(), a.getCreatedAt());
        }
    }

    public record AuditLogCreateDto(
        UUID userId,
        UUID tenantId,
        @NotBlank String entityType,
        UUID entityId,
        @NotBlank String action,
        Map<String, Object> diff,
        String ipAddress,
        String userAgent
    ) {}

    // ─── SystemEvent ─────────────────────────────────────────────────────────

    public record SystemEventDto(UUID id, String type, Map<String, Object> payload, Instant processedAt,
                                 Instant createdAt) {
        public static SystemEventDto from(SystemEvent e) {
            return new SystemEventDto(e.getId(), e.getType(), e.getPayload(), e.getProcessedAt(), e.getCreatedAt());
        }
    }

    public record SystemEventCreateDto(
        @NotBlank String type,
        @NotNull Map<String, Object> payload
    ) {}

    // ─── ErrorLog ────────────────────────────────────────────────────────────

    public record ErrorLogDto(UUID id, String serviceName, String message, String stacktrace, String severity,
                              Map<String, Object> metadata, Instant createdAt) {
        public static ErrorLogDto from(ErrorLog e) {
            return new ErrorLogDto(e.getId(), e.getServiceName(), e.getMessage(), e.getStacktrace(),
                e.getSeverity(), e.getMetadata(), e.getCreatedAt());
        }
    }

    public record ErrorLogCreateDto(
        @NotBlank String serviceName,
        @NotBlank String message,
        String stacktrace,
        @Pattern(regexp = "^(debug|info|warn|error|fatal)$") String severity
    ) {}

    // ─── JobExecution ────────────────────────────────────────────────────────

    public record JobExecutionDto(UUID id, String jobName, String status, Instant startedAt,
                                  Instant finishedAt, Map<String, Object> result, String errorMessage) {
        public static JobExecutionDto from(JobExecution j) {
            return new JobExecutionDto(j.getId(), j.getJobName(), j.getStatus(), j.getStartedAt(),
                j.getFinishedAt(), j.getResult(), j.getErrorMessage());
        }
    }
}
