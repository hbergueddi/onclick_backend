package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantStaffDto;
import com.onesley.oneclick.entity.restaurant.RestaurantStaff;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code RestaurantStaff ↔ RestaurantStaffDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantStaffMapper {

    RestaurantStaffDto toDto(RestaurantStaff entity);

    List<RestaurantStaffDto> toDtoList(List<RestaurantStaff> entities);
}
