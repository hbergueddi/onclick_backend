package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.ActionLogDto;
import com.onesley.oneclick.entity.admin.ActionLog;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code ActionLog ↔ ActionLogDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface ActionLogMapper {

    ActionLogDto toDto(ActionLog entity);

    List<ActionLogDto> toDtoList(List<ActionLog> entities);
}
