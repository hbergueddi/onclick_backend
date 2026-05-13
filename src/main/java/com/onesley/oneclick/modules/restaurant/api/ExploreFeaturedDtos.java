package com.onesley.oneclick.modules.restaurant.api;

import com.onesley.oneclick.modules.restaurant.internal.ExploreFeatured;

import java.time.Instant;
import java.util.UUID;

public final class ExploreFeaturedDtos {

    private ExploreFeaturedDtos() {}

    public record ExploreFeaturedDto(
        UUID id,
        UUID restaurantId,
        Integer rank,
        Boolean enabled,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt
    ) {
        public static ExploreFeaturedDto from(ExploreFeatured f) {
            return new ExploreFeaturedDto(
                f.getId(), f.getRestaurantId(), f.getRank(), f.getEnabled(),
                f.getStartsAt(), f.getEndsAt(), f.getCreatedAt()
            );
        }
    }

    public record ExploreFeaturedCreateDto(
        UUID restaurantId,
        Integer rank,
        Boolean enabled,
        Instant startsAt,
        Instant endsAt
    ) {}
}
