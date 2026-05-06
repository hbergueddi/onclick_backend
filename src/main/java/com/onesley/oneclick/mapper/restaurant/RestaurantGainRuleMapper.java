package com.onesley.oneclick.mapper.restaurant;

import com.onesley.oneclick.dto.restaurant.RestaurantGainRuleDto;
import com.onesley.oneclick.entity.restaurant.RestaurantGainRule;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code RestaurantGainRule ↔ RestaurantGainRuleDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface RestaurantGainRuleMapper {

    RestaurantGainRuleDto toDto(RestaurantGainRule entity);

    List<RestaurantGainRuleDto> toDtoList(List<RestaurantGainRule> entities);
}
