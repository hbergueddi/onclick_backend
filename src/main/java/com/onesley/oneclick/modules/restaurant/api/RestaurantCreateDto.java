package com.onesley.oneclick.modules.restaurant.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RestaurantCreateDto(
    @NotNull UUID tenantId,
    @NotBlank @Size(min = 1, max = 128) String name,
    @Size(min = 1, max = 1024) String description,
    @Size(min = 1, max = 64) String phone,
    @Size(min = 1, max = 256) String address,
    @NotBlank @Size(min = 1, max = 128) String city,
    BigDecimal latitude,
    BigDecimal longitude,
    @Size(min = 1, max = 64) String cuisine,
    Integer maxStaff,
    UUID groupId
) {
}
