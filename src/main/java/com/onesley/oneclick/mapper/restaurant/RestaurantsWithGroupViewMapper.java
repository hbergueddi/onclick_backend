package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantsWithGroupViewDto;
import com.onesley.oneclick.entity.restaurant.RestaurantsWithGroupView;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code RestaurantsWithGroupView ↔ RestaurantsWithGroupViewDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantsWithGroupViewMapper {

    RestaurantsWithGroupViewDto toDto(RestaurantsWithGroupView entity);

    List<RestaurantsWithGroupViewDto> toDtoList(List<RestaurantsWithGroupView> entities);
}
