package com.onesley.oneclick.mapper.support;

import com.onesley.oneclick.dto.support.ClientVisibleRatingDto;
import com.onesley.oneclick.entity.support.ClientVisibleRating;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ClientVisibleRating ↔ ClientVisibleRatingDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ClientVisibleRatingMapper {

    ClientVisibleRatingDto toDto(ClientVisibleRating entity);

    List<ClientVisibleRatingDto> toDtoList(List<ClientVisibleRating> entities);
}
