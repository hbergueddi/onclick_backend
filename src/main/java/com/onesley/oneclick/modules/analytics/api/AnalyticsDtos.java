package com.onesley.oneclick.modules.analytics.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs publics du module analytics.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Entity.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {}

    // ─── ApiClient ───────────────────────────────────────────────────────────

    public record ApiClientDto(UUID id, UUID tenantId, String name, String description, boolean enabled,
                               Instant createdAt) {}

    public record ApiClientCreateDto(
        @NotBlank @Size(min = 1, max = 128) String name,
        @Size(min = 1, max = 1024) String description,
        UUID tenantId
    ) {}

    // ─── ApiKey ──────────────────────────────────────────────────────────────

    public record ApiKeyDto(UUID id, UUID apiClientId, String keyPrefix, List<String> scopes, boolean enabled,
                            Instant lastUsedAt, Instant expiresAt, Instant createdAt, Instant revokedAt) {}

    public record ApiKeyCreateDto(
        @NotNull UUID apiClientId,
        @NotBlank @Size(min = 1, max = 128) String keyHash,
        @NotBlank @Size(min = 1, max = 64) String keyPrefix,
        Instant expiresAt
    ) {}

    // ─── Webhook ─────────────────────────────────────────────────────────────

    public record WebhookDto(UUID id, UUID apiClientId, String url, List<String> eventTypes, boolean enabled,
                             Instant createdAt) {}

    public record WebhookCreateDto(
        @NotNull UUID apiClientId,
        @NotBlank @Size(min = 1, max = 512) String url,
        @Size(min = 1, max = 64) String secret
    ) {}

    // ─── WebhookDelivery ─────────────────────────────────────────────────────

    public record WebhookDeliveryDto(UUID id, UUID webhookId, String eventType, Map<String, Object> payload,
                                     Integer statusCode, Integer attempts, Instant succeededAt, Instant failedAt,
                                     Instant createdAt) {}

    public record WebhookDeliveryCreateDto(
        @NotNull UUID webhookId,
        @NotBlank @Size(min = 1, max = 64) String eventType,
        @NotNull Map<String, Object> payload
    ) {}
}
