package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantDto;
import com.onesley.oneclick.entity.restaurant.Restaurant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface RestaurantMapper {

    @Mapping(target = "status", source = "status.code")
    RestaurantDto toDto(Restaurant entity);

    List<RestaurantDto> toDtoList(List<Restaurant> entities);
}
