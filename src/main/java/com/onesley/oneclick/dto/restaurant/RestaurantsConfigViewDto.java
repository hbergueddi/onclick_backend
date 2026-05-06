package com.onesley.oneclick.dto.restaurant;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO read-only pour {@code v_restaurants_config} (généré par scripts/scaffold-jpa.mjs).
 */
public record RestaurantsConfigViewDto(
    UUID id,
    String name,
    String phone,
    String address,
    String description,
    List<String> tags,
    Integer maxStaff,
    Boolean openNow
) {
}
