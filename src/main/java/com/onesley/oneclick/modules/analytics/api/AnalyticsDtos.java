package com.onesley.oneclick.modules.analytics.api;

import jakarta.validation.constraints.NotBlank;
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
        @NotBlank String name,
        String description,
        UUID tenantId
    ) {}

    // ─── ApiKey ──────────────────────────────────────────────────────────────

    public record ApiKeyDto(UUID id, UUID apiClientId, String keyPrefix, List<String> scopes, boolean enabled,
                            Instant lastUsedAt, Instant expiresAt, Instant createdAt, Instant revokedAt) {}

    public record ApiKeyCreateDto(
        @NotNull UUID apiClientId,
        @NotBlank String keyHash,
        @NotBlank String keyPrefix,
        Instant expiresAt
    ) {}

    // ─── Webhook ─────────────────────────────────────────────────────────────

    public record WebhookDto(UUID id, UUID apiClientId, String url, List<String> eventTypes, boolean enabled,
                             Instant createdAt) {}

    public record WebhookCreateDto(
        @NotNull UUID apiClientId,
        @NotBlank String url,
        String secret
    ) {}

    // ─── WebhookDelivery ─────────────────────────────────────────────────────

    public record WebhookDeliveryDto(UUID id, UUID webhookId, String eventType, Map<String, Object> payload,
                                     Integer statusCode, Integer attempts, Instant succeededAt, Instant failedAt,
                                     Instant createdAt) {}

    public record WebhookDeliveryCreateDto(
        @NotNull UUID webhookId,
        @NotBlank String eventType,
        @NotNull Map<String, Object> payload
    ) {}
}
