package com.onesley.oneclick.mapper.support;

import com.onesley.oneclick.dto.support.NoShowDisputeDto;
import com.onesley.oneclick.entity.support.NoShowDispute;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@code NoShowDispute ↔ NoShowDisputeDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface NoShowDisputeMapper {

    @Mapping(target = "status", source = "status.code")
    NoShowDisputeDto toDto(NoShowDispute entity);

    List<NoShowDisputeDto> toDtoList(List<NoShowDispute> entities);
}
