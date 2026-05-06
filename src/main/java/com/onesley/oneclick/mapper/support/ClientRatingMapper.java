package com.onesley.oneclick.mapper.support;

import com.onesley.oneclick.dto.support.ClientRatingDto;
import com.onesley.oneclick.entity.support.ClientRating;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ClientRating ↔ ClientRatingDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ClientRatingMapper {

    ClientRatingDto toDto(ClientRating entity);

    List<ClientRatingDto> toDtoList(List<ClientRating> entities);
}
