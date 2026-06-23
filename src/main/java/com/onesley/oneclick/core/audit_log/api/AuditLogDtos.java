package com.onesley.oneclick.core.audit_log.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    /**
     * Une entrée du journal d'audit.
     *
     * <p>{@code userName} = "Prénom Nom" de l'auteur ({@code userId}), résolu en lecture
     * de liste via {@code UserDirectoryApi.namesByIds} (anti-N+1) pour que le « Journal des
     * actions » (ProDesk) affiche un nom plutôt qu'un UUID. <b>Null</b> sur les écritures
     * ({@code recordAudit}) et pour les actions système (userId null) ou les utilisateurs
     * supprimés — l'enrichissement n'introduit <b>aucune nouvelle autorité</b> (la lecture
     * reste {@code VIEW:AUDIT}).</p>
     */
    public record AuditLogDto(UUID id, UUID userId, String userName, UUID tenantId, String entityType,
                              UUID entityId, String action, Map<String, Object> diff, String ipAddress,
                              String userAgent, Instant createdAt) {

        /** Copie enrichie du nom d'auteur (préserve tous les autres champs). */
        public AuditLogDto withUserName(String resolvedUserName) {
            return new AuditLogDto(id, userId, resolvedUserName, tenantId, entityType, entityId,
                action, diff, ipAddress, userAgent, createdAt);
        }
    }

    public record AuditLogCreateDto(
        UUID userId,
        UUID tenantId,
        @NotBlank @Size(min = 1, max = 64) String entityType,
        UUID entityId,
        @NotBlank @Size(min = 1, max = 64) String action,
        Map<String, Object> diff,
        @Size(min = 1, max = 256) String ipAddress,
        @Size(min = 1, max = 512) String userAgent
    ) {}

    // ─── SystemEvent ─────────────────────────────────────────────────────────

    public record SystemEventDto(UUID id, String type, Map<String, Object> payload, Instant processedAt,
                                 Instant createdAt) {}

    public record SystemEventCreateDto(
        @NotBlank @Size(min = 1, max = 64) String type,
        @NotNull Map<String, Object> payload
    ) {}

    // ─── ErrorLog ────────────────────────────────────────────────────────────

    public record ErrorLogDto(UUID id, String serviceName, String message, String stacktrace, String severity,
                              Map<String, Object> metadata, Instant createdAt) {}

    public record ErrorLogCreateDto(
        @NotBlank @Size(min = 1, max = 128) String serviceName,
        @NotBlank @Size(min = 1, max = 1024) String message,
        @Size(min = 1, max = 64) String stacktrace,
        @Pattern(regexp = "^(debug|info|warn|error|fatal)$") @Size(min = 1, max = 64) String severity
    ) {}

    // ─── JobExecution ────────────────────────────────────────────────────────

    public record JobExecutionDto(UUID id, String jobName, String status, Instant startedAt,
                                  Instant finishedAt, Map<String, Object> result, String errorMessage) {}
}
