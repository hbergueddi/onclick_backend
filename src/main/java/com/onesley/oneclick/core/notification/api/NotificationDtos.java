package com.onesley.oneclick.core.notification.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import com.onesley.oneclick.core.notification.internal.DeviceToken;
import com.onesley.oneclick.core.notification.internal.Notification;
import com.onesley.oneclick.core.notification.internal.NotificationCampaign;

public final class NotificationDtos {

    private NotificationDtos() {}

    // ─── Notification ────────────────────────────────────────────────────────

    public record NotificationDto(UUID id, UUID recipientUserId, String type, String channel, String title,
                                  String body, String link, Map<String, Object> metadata, Instant readAt,
                                  Instant createdAt) {
        public static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getRecipientUserId(), n.getType(), n.getChannel(),
                n.getTitle(), n.getBody(), n.getLink(), n.getMetadata(), n.getReadAt(), n.getCreatedAt());
        }
    }

    public record NotificationCreateDto(
        @NotNull UUID recipientUserId,
        @NotBlank @Pattern(regexp = "^(reservation|loyalty|promotion|community|support|system|announcement)$") String type,
        @Pattern(regexp = "^(inapp|push|email|sms)$") String channel,
        @NotBlank String title,
        @NotBlank String body,
        String link
    ) {}

    // ─── Campaign ────────────────────────────────────────────────────────────

    public record CampaignDto(UUID id, UUID tenantId, String title, String message, String targetSegment,
                              Instant scheduledAt, Instant sentAt, String status, UUID createdById,
                              Instant createdAt) {
        public static CampaignDto from(NotificationCampaign c) {
            return new CampaignDto(c.getId(), c.getTenantId(), c.getTitle(), c.getMessage(),
                c.getTargetSegment(), c.getScheduledAt(), c.getSentAt(), c.getStatus(),
                c.getCreatedById(), c.getCreatedAt());
        }
    }

    public record CampaignCreateDto(
        @NotNull UUID tenantId,
        @NotBlank String title,
        @NotBlank String message,
        String targetSegment,
        Instant scheduledAt
    ) {}

    // ─── DeviceToken ─────────────────────────────────────────────────────────

    public record DeviceTokenDto(UUID id, UUID userId, String token, String platform, String appId,
                                 Instant lastUsedAt, Instant createdAt) {
        public static DeviceTokenDto from(DeviceToken t) {
            return new DeviceTokenDto(t.getId(), t.getUserId(), t.getToken(), t.getPlatform(),
                t.getAppId(), t.getLastUsedAt(), t.getCreatedAt());
        }
    }

    public record DeviceTokenCreateDto(
        @NotNull UUID userId,
        @NotBlank String token,
        @Pattern(regexp = "^(ios|android|web)$") String platform,
        String appId
    ) {}
}
