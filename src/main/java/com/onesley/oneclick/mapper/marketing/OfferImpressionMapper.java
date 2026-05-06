package com.onesley.oneclick.mapper.marketing;

import com.onesley.oneclick.dto.marketing.OfferImpressionDto;
import com.onesley.oneclick.entity.marketing.OfferImpression;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code OfferImpression ↔ OfferImpressionDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface OfferImpressionMapper {

    OfferImpressionDto toDto(OfferImpression entity);

    List<OfferImpressionDto> toDtoList(List<OfferImpression> entities);
}
