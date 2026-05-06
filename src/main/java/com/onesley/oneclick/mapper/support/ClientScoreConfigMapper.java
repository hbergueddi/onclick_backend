package com.onesley.oneclick.mapper.support;

import com.onesley.oneclick.dto.support.ClientScoreConfigDto;
import com.onesley.oneclick.entity.support.ClientScoreConfig;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ClientScoreConfig ↔ ClientScoreConfigDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ClientScoreConfigMapper {

    ClientScoreConfigDto toDto(ClientScoreConfig entity);

    List<ClientScoreConfigDto> toDtoList(List<ClientScoreConfig> entities);
}
