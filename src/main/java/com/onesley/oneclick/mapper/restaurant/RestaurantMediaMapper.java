package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantMediaDto;
import com.onesley.oneclick.entity.restaurant.RestaurantMedia;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@code RestaurantMedia ↔ RestaurantMediaDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantMediaMapper {

    @Mapping(target = "status", source = "status.code")
    RestaurantMediaDto toDto(RestaurantMedia entity);

    List<RestaurantMediaDto> toDtoList(List<RestaurantMedia> entities);
}
