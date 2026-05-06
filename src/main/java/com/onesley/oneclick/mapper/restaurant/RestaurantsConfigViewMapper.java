package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantsConfigViewDto;
import com.onesley.oneclick.entity.restaurant.RestaurantsConfigView;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code RestaurantsConfigView ↔ RestaurantsConfigViewDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantsConfigViewMapper {

    RestaurantsConfigViewDto toDto(RestaurantsConfigView entity);

    List<RestaurantsConfigViewDto> toDtoList(List<RestaurantsConfigView> entities);
}
