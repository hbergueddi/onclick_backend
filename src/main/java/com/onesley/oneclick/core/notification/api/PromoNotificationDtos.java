package com.onesley.oneclick.core.notification.api;

import jakarta.validation.constraints.Size;

import com.onesley.oneclick.core.notification.internal.PromoNotificationRequest;

import java.time.Instant;
import java.util.UUID;

public final class PromoNotificationDtos {

    private PromoNotificationDtos() {}

    public record PromoRequestDto(
        UUID id,
        UUID tenantId,
        UUID restaurantId,
        UUID offerId,
        String title,
        String body,
        String segment,
        String status,
        UUID requestedBy,
        UUID reviewedBy,
        Instant reviewedAt,
        String rejectionReason,
        Instant pushSentAt,
        Integer pushSentCount,
        String pushError,
        Instant createdAt
    ) {
        public static PromoRequestDto from(PromoNotificationRequest p) {
            return new PromoRequestDto(
                p.getId(), p.getTenantId(), p.getRestaurantId(), p.getOfferId(),
                p.getTitle(), p.getBody(), p.getSegment(), p.getStatus(),
                p.getRequestedBy(), p.getReviewedBy(), p.getReviewedAt(),
                p.getRejectionReason(), p.getPushSentAt(), p.getPushSentCount(),
                p.getPushError(), p.getCreatedAt()
            );
        }
    }

    public record PromoRequestCreateDto(
        UUID tenantId,
        UUID restaurantId,
        UUID offerId,
        @Size(min = 1, max = 128) String title,
        @Size(min = 1, max = 1024) String body,
        @Size(min = 1, max = 64) String segment,
        UUID requestedBy
    ) {}

    public record PromoRequestReviewDto(
        @Size(min = 1, max = 64) String status,
        UUID reviewedBy,
        @Size(min = 1, max = 1024) String rejectionReason
    ) {}

    public record PromoStatsDto(
        Long totalOffers,
        Long activeOffers,
        Long viewsLast30d,
        Long redemptionsLast30d,
        Long pushSentLast30d
    ) {}
}
