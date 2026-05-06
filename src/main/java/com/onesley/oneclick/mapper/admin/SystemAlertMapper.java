package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.SystemAlertDto;
import com.onesley.oneclick.entity.admin.SystemAlert;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code SystemAlert ↔ SystemAlertDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface SystemAlertMapper {

    SystemAlertDto toDto(SystemAlert entity);

    List<SystemAlertDto> toDtoList(List<SystemAlert> entities);
}
