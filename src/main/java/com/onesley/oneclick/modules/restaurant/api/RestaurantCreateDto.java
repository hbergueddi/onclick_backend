package com.onesley.oneclick.modules.restaurant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RestaurantCreateDto(
    @NotNull UUID tenantId,
    @NotBlank String name,
    String description,
    String phone,
    String address,
    @NotBlank String city,
    BigDecimal latitude,
    BigDecimal longitude,
    String cuisine,
    Integer maxStaff,
    UUID groupId
) {
}
