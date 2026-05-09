package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantZoneDto;
import com.onesley.oneclick.entity.restaurant.RestaurantZone;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@code RestaurantZone ↔ RestaurantZoneDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantZoneMapper {

    @Mapping(target = "status", source = "status.code")
    RestaurantZoneDto toDto(RestaurantZone entity);

    List<RestaurantZoneDto> toDtoList(List<RestaurantZone> entities);
}
