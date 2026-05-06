package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantTierStatuDto;
import com.onesley.oneclick.entity.restaurant.RestaurantTierStatu;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code RestaurantTierStatu ↔ RestaurantTierStatuDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantTierStatuMapper {

    RestaurantTierStatuDto toDto(RestaurantTierStatu entity);

    List<RestaurantTierStatuDto> toDtoList(List<RestaurantTierStatu> entities);
}
