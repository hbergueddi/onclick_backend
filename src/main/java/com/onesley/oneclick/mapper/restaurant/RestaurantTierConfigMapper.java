package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantTierConfigDto;
import com.onesley.oneclick.entity.restaurant.RestaurantTierConfig;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code RestaurantTierConfig ↔ RestaurantTierConfigDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantTierConfigMapper {

    RestaurantTierConfigDto toDto(RestaurantTierConfig entity);

    List<RestaurantTierConfigDto> toDtoList(List<RestaurantTierConfig> entities);
}
