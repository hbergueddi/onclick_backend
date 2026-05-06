package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantExpiredPoolDto;
import com.onesley.oneclick.entity.restaurant.RestaurantExpiredPool;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code RestaurantExpiredPool ↔ RestaurantExpiredPoolDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantExpiredPoolMapper {

    RestaurantExpiredPoolDto toDto(RestaurantExpiredPool entity);

    List<RestaurantExpiredPoolDto> toDtoList(List<RestaurantExpiredPool> entities);
}
