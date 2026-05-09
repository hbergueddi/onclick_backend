package com.onesley.oneclick.mapper.loyalty;

import com.onesley.oneclick.dto.loyalty.EliteApplicationDto;
import com.onesley.oneclick.entity.loyalty.EliteApplication;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Mapper {@code EliteApplication ↔ EliteApplicationDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface EliteApplicationMapper {

    @Mapping(target = "status", source = "status.code")
    EliteApplicationDto toDto(EliteApplication entity);

    List<EliteApplicationDto> toDtoList(List<EliteApplication> entities);
}
