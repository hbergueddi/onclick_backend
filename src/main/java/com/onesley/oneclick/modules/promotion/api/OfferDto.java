package com.onesley.oneclick.modules.promotion.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.onesley.oneclick.modules.promotion.internal.Offer;

public record OfferDto(
    UUID id, UUID restaurantId, String title, String description,
    Instant startsAt, Instant expiresAt, BigDecimal discountPct, BigDecimal discountAmount,
    boolean enabled, Instant createdAt
) {
    public static OfferDto from(Offer o) {
        return new OfferDto(o.getId(), o.getRestaurantId(), o.getTitle(), o.getDescription(),
            o.getStartsAt(), o.getExpiresAt(), o.getDiscountPct(), o.getDiscountAmount(),
            o.isEnabled(), o.getCreatedAt());
    }
}
