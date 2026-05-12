package com.onesley.oneclick.core.notification.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs publics du module notification.
 *
 * <p>Pas de méthode de mapping ici : la conversion Entity → DTO se fait via
 * {@code Entity.toDto()} dans le package {@code internal} (dépendance
 * internal → api autorisée en Modulith CLOSED).</p>
 */
public final class NotificationDtos {

    private NotificationDtos() {}

    // ─── Notification ────────────────────────────────────────────────────────

    public record NotificationDto(UUID id, UUID recipientUserId, String type, String channel, String title,
                                  String body, String link, Map<String, Object> metadata, Instant readAt,
                                  Instant createdAt) {}

    public record NotificationCreateDto(
        @NotNull UUID recipientUserId,
        @NotBlank @Pattern(regexp = "^(reservation|loyalty|promotion|community|support|system|announcement)$") String type,
        @Pattern(regexp = "^(inapp|push|email|sms)$") String channel,
        @NotBlank String title,
        @NotBlank String body,
        String link
    ) {}

    /** Compteur de notifications non lues pour un user (badge cloche). */
    public record UnreadCountDto(long count) {}

    /** Résultat de l'opération bulk "marquer tout comme lu". */
    public record MarkAllReadResultDto(long updated) {}

    // ─── Campaign ────────────────────────────────────────────────────────────

    public record CampaignDto(UUID id, UUID tenantId, String title, String message, String targetSegment,
                              Instant scheduledAt, Instant sentAt, String status, UUID createdById,
                              Instant createdAt) {}

    public record CampaignCreateDto(
        @NotNull UUID tenantId,
        @NotBlank String title,
        @NotBlank String message,
        String targetSegment,
        Instant scheduledAt
    ) {}

    // ─── DeviceToken ─────────────────────────────────────────────────────────

    public record DeviceTokenDto(UUID id, UUID userId, String token, String platform, String appId,
                                 Instant lastUsedAt, Instant createdAt) {}

    public record DeviceTokenCreateDto(
        @NotNull UUID userId,
        @NotBlank String token,
        @Pattern(regexp = "^(ios|android|web)$") String platform,
        String appId
    ) {}
}
