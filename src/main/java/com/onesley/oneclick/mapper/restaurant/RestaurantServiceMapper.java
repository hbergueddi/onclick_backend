package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantServiceDto;
import com.onesley.oneclick.entity.restaurant.RestaurantService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@code RestaurantService ↔ RestaurantServiceDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantServiceMapper {

    @Mapping(target = "status", source = "status.code")
    RestaurantServiceDto toDto(RestaurantService entity);

    List<RestaurantServiceDto> toDtoList(List<RestaurantService> entities);
}
