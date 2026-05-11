package com.onesley.oneclick.modules.analytics;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnalyticsDtos {

    private AnalyticsDtos() {}
    

    // ─── ApiClient ───────────────────────────────────────────────────────────

    public record ApiClientDto(UUID id, UUID tenantId, String name, String description, boolean enabled,
                               Instant createdAt) {
        public static ApiClientDto from(ApiClient c) {
            return new ApiClientDto(c.getId(), c.getTenantId(), c.getName(), c.getDescription(),
                c.isEnabled(), c.getCreatedAt());
        }
    }

    public record ApiClientCreateDto(
        @NotBlank String name,
        String description,
        UUID tenantId
    ) {}

    // ─── ApiKey ──────────────────────────────────────────────────────────────

    public record ApiKeyDto(UUID id, UUID apiClientId, String keyPrefix, List<String> scopes, boolean enabled,
                            Instant lastUsedAt, Instant expiresAt, Instant createdAt, Instant revokedAt) {
        public static ApiKeyDto from(ApiKey k) {
            return new ApiKeyDto(k.getId(), k.getApiClientId(), k.getKeyPrefix(), k.getScopes(),
                k.isEnabled(), k.getLastUsedAt(), k.getExpiresAt(), k.getCreatedAt(), k.getRevokedAt());
        }
    }

    public record ApiKeyCreateDto(
        @NotNull UUID apiClientId,
        @NotBlank String keyHash,
        @NotBlank String keyPrefix,
        Instant expiresAt
    ) {}

    // ─── Webhook ─────────────────────────────────────────────────────────────

    public record WebhookDto(UUID id, UUID apiClientId, String url, List<String> eventTypes, boolean enabled,
                             Instant createdAt) {
        public static WebhookDto from(Webhook w) {
            return new WebhookDto(w.getId(), w.getApiClientId(), w.getUrl(), w.getEventTypes(),
                w.isEnabled(), w.getCreatedAt());
        }
    }

    public record WebhookCreateDto(
        @NotNull UUID apiClientId,
        @NotBlank String url,
        String secret
    ) {}

    // ─── WebhookDelivery ─────────────────────────────────────────────────────

    public record WebhookDeliveryDto(UUID id, UUID webhookId, String eventType, Map<String, Object> payload,
                                     Integer statusCode, Integer attempts, Instant succeededAt, Instant failedAt,
                                     Instant createdAt) {
        public static WebhookDeliveryDto from(WebhookDelivery d) {
            return new WebhookDeliveryDto(d.getId(), d.getWebhookId(), d.getEventType(), d.getPayload(),
                d.getStatusCode(), d.getAttempts(), d.getSucceededAt(), d.getFailedAt(), d.getCreatedAt());
        }
    }

    public record WebhookDeliveryCreateDto(
        @NotNull UUID webhookId,
        @NotBlank String eventType,
        @NotNull Map<String, Object> payload
    ) {}
}
