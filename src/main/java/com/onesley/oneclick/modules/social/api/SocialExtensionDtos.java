package com.onesley.oneclick.modules.social.api;

import jakarta.validation.constraints.Size;

import com.onesley.oneclick.modules.social.internal.EliteApplication;
import com.onesley.oneclick.modules.social.internal.RestaurantGroup;

import java.time.Instant;
import java.util.UUID;

public final class SocialExtensionDtos {

    private SocialExtensionDtos() {}

    public record EliteApplicationDto(
        UUID id,
        UUID tenantId,
        UUID userId,
        String status,
        String motivation,
        UUID referrerId,
        UUID reviewedBy,
        Instant reviewedAt,
        String rejectionReason,
        Instant createdAt
    ) {
        public static EliteApplicationDto from(EliteApplication a) {
            return new EliteApplicationDto(
                a.getId(), a.getTenantId(), a.getUserId(), a.getStatus(),
                a.getMotivation(), a.getReferrerId(), a.getReviewedBy(),
                a.getReviewedAt(), a.getRejectionReason(), a.getCreatedAt()
            );
        }
    }

    public record EliteApplicationCreateDto(
        UUID userId,
        @Size(min = 1, max = 1024) String motivation,
        UUID referrerId
    ) {}

    public record EliteApplicationReviewDto(
        @Size(min = 1, max = 64) String status,
        UUID reviewedBy,
        @Size(min = 1, max = 1024) String rejectionReason
    ) {}

    public record RestaurantGroupDto(
        UUID id,
        UUID tenantId,
        String name,
        String description,
        UUID ownerId,
        String logoUrl,
        String status,
        Instant createdAt
    ) {
        public static RestaurantGroupDto from(RestaurantGroup g) {
            return new RestaurantGroupDto(
                g.getId(), g.getTenantId(), g.getName(), g.getDescription(),
                g.getOwnerId(), g.getLogoUrl(), g.getStatus(), g.getCreatedAt()
            );
        }
    }

    public record RestaurantGroupCreateDto(
        UUID tenantId,
        @Size(min = 1, max = 128) String name,
        @Size(min = 1, max = 1024) String description,
        UUID ownerId,
        @Size(min = 1, max = 512) String logoUrl
    ) {}
}
