package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantsGoogleViewDto;
import com.onesley.oneclick.entity.restaurant.RestaurantsGoogleView;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code RestaurantsGoogleView ↔ RestaurantsGoogleViewDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantsGoogleViewMapper {

    RestaurantsGoogleViewDto toDto(RestaurantsGoogleView entity);

    List<RestaurantsGoogleViewDto> toDtoList(List<RestaurantsGoogleView> entities);
}
