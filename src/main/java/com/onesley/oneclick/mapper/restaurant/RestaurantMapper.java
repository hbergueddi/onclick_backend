package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantDto;
import com.onesley.oneclick.entity.restaurant.Restaurant;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface RestaurantMapper {

    RestaurantDto toDto(Restaurant entity);

    List<RestaurantDto> toDtoList(List<Restaurant> entities);
}
