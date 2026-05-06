package com.onesley.oneclick.mapper.pcc;

import com.onesley.oneclick.dto.pcc.SeminarRequestDto;
import com.onesley.oneclick.entity.pcc.SeminarRequest;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code SeminarRequest ↔ SeminarRequestDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface SeminarRequestMapper {

    SeminarRequestDto toDto(SeminarRequest entity);

    List<SeminarRequestDto> toDtoList(List<SeminarRequest> entities);
}
