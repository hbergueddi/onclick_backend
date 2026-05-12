package com.onesley.oneclick.core.audit_log.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs publics du module audit_log.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Entity.toDto()} (dépendance internal → api autorisée en Modulith
 * CLOSED ; {@code SystemEvent} étant exposé via api/, son {@code toDto()}
 * reste local au package).</p>
 */
public final class AuditLogDtos {

    private AuditLogDtos() {}

    // ─── AuditLog ────────────────────────────────────────────────────────────

    public record AuditLogDto(UUID id, UUID userId, UUID tenantId, String entityType, UUID entityId,
                              String action, Map<String, Object> diff, String ipAddress, String userAgent,
                              Instant createdAt) {}

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
                                 Instant createdAt) {}

    public record SystemEventCreateDto(
        @NotBlank String type,
        @NotNull Map<String, Object> payload
    ) {}

    // ─── ErrorLog ────────────────────────────────────────────────────────────

    public record ErrorLogDto(UUID id, String serviceName, String message, String stacktrace, String severity,
                              Map<String, Object> metadata, Instant createdAt) {}

    public record ErrorLogCreateDto(
        @NotBlank String serviceName,
        @NotBlank String message,
        String stacktrace,
        @Pattern(regexp = "^(debug|info|warn|error|fatal)$") String severity
    ) {}

    // ─── JobExecution ────────────────────────────────────────────────────────

    public record JobExecutionDto(UUID id, String jobName, String status, Instant startedAt,
                                  Instant finishedAt, Map<String, Object> result, String errorMessage) {}
}
