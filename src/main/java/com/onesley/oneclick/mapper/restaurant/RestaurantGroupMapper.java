package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantGroupDto;
import com.onesley.oneclick.entity.restaurant.RestaurantGroup;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code RestaurantGroup ↔ RestaurantGroupDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantGroupMapper {

    RestaurantGroupDto toDto(RestaurantGroup entity);

    List<RestaurantGroupDto> toDtoList(List<RestaurantGroup> entities);
}
