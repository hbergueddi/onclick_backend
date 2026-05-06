package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.TierRestaurantOfferDto;
import com.onesley.oneclick.entity.loyalty.TierRestaurantOffer;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code TierRestaurantOffer ↔ TierRestaurantOfferDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface TierRestaurantOfferMapper {

    TierRestaurantOfferDto toDto(TierRestaurantOffer entity);

    List<TierRestaurantOfferDto> toDtoList(List<TierRestaurantOffer> entities);
}
