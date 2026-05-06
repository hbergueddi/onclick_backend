package com.onesley.oneclick.mapper.marketing;

import com.onesley.oneclick.dto.marketing.OfferDto;
import com.onesley.oneclick.entity.marketing.Offer;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code Offer ↔ OfferDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface OfferMapper {

    OfferDto toDto(Offer entity);

    List<OfferDto> toDtoList(List<Offer> entities);
}
