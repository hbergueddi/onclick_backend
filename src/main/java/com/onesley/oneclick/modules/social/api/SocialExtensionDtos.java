package com.onesley.oneclick.modules.social.api;

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
        String motivation,
        UUID referrerId
    ) {}

    public record EliteApplicationReviewDto(
        String status,
        UUID reviewedBy,
        String rejectionReason
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
        String name,
        String description,
        UUID ownerId,
        String logoUrl
    ) {}
}
