package com.onesley.oneclick.modules.restaurant.api;

import com.onesley.oneclick.modules.restaurant.internal.ExploreFeatured;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

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
        String label,
        String notes,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
    ) {
        public static ExploreFeaturedDto from(ExploreFeatured f) {
            return new ExploreFeaturedDto(
                f.getId(), f.getRestaurantId(), f.getRank(), f.getEnabled(),
                f.getStartsAt(), f.getEndsAt(),
                f.getLabel(), f.getNotes(), f.getCreatedBy(),
                f.getCreatedAt(), f.getUpdatedAt()
            );
        }
    }

    public record ExploreFeaturedCreateDto(
        UUID restaurantId,
        @PositiveOrZero Integer rank,
        Boolean enabled,
        Instant startsAt,
        Instant endsAt,
        @Size(max = 256) String label,
        @Size(max = 2000) String notes,
        UUID createdBy
    ) {}
}
