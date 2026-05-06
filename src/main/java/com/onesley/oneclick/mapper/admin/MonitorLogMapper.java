package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.MonitorLogDto;
import com.onesley.oneclick.entity.admin.MonitorLog;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code MonitorLog ↔ MonitorLogDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface MonitorLogMapper {

    MonitorLogDto toDto(MonitorLog entity);

    List<MonitorLogDto> toDtoList(List<MonitorLog> entities);
}
