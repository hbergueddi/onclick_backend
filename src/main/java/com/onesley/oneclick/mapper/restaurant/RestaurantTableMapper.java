package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantTableDto;
import com.onesley.oneclick.entity.restaurant.RestaurantTable;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code RestaurantTable ↔ RestaurantTableDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantTableMapper {

    RestaurantTableDto toDto(RestaurantTable entity);

    List<RestaurantTableDto> toDtoList(List<RestaurantTable> entities);
}
