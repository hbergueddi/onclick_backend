package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantRestitutionDto;
import com.onesley.oneclick.entity.restaurant.RestaurantRestitution;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@code RestaurantRestitution ↔ RestaurantRestitutionDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantRestitutionMapper {

    @Mapping(target = "status", source = "status.code")
    RestaurantRestitutionDto toDto(RestaurantRestitution entity);

    List<RestaurantRestitutionDto> toDtoList(List<RestaurantRestitution> entities);
}
