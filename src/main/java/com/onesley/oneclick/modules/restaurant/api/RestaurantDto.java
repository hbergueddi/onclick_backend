package com.onesley.oneclick.modules.restaurant.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import com.onesley.oneclick.modules.restaurant.internal.Restaurant;

public record RestaurantDto(
    UUID id,
    UUID tenantId,
    String name,
    String description,
    String phone,
    String address,
    String city,
    BigDecimal latitude,
    BigDecimal longitude,
    String status,
    Instant createdAt
) {
    public static RestaurantDto from(Restaurant r) {
        return new RestaurantDto(
            r.getId(), r.getTenantId(), r.getName(), r.getDescription(),
            r.getPhone(), r.getAddress(), r.getCity(),
            r.getLatitude(), r.getLongitude(),
            r.getStatus(), r.getCreatedAt()
        );
    }
}
