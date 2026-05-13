package com.onesley.oneclick.core.notification.api;

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
        String title,
        String body,
        String segment,
        UUID requestedBy
    ) {}

    public record PromoRequestReviewDto(
        String status,
        UUID reviewedBy,
        String rejectionReason
    ) {}

    public record PromoStatsDto(
        Long totalOffers,
        Long activeOffers,
        Long viewsLast30d,
        Long redemptionsLast30d,
        Long pushSentLast30d
    ) {}
}
