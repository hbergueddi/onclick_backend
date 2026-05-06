package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantKpisViewDto;
import com.onesley.oneclick.entity.restaurant.RestaurantKpisView;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code RestaurantKpisView ↔ RestaurantKpisViewDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantKpisViewMapper {

    RestaurantKpisViewDto toDto(RestaurantKpisView entity);

    List<RestaurantKpisViewDto> toDtoList(List<RestaurantKpisView> entities);
}
