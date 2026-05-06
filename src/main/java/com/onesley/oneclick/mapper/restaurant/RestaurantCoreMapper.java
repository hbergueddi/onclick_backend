package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantCoreDto;
import com.onesley.oneclick.entity.restaurant.RestaurantCoreView;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper
public interface RestaurantCoreMapper {

    RestaurantCoreDto toDto(RestaurantCoreView view);

    List<RestaurantCoreDto> toDtoList(List<RestaurantCoreView> views);
}
