package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.SystemHealthCheckDto;
import com.onesley.oneclick.entity.admin.SystemHealthCheck;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code SystemHealthCheck ↔ SystemHealthCheckDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface SystemHealthCheckMapper {

    SystemHealthCheckDto toDto(SystemHealthCheck entity);

    List<SystemHealthCheckDto> toDtoList(List<SystemHealthCheck> entities);
}
