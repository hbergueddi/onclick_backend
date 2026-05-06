package com.onesley.oneclick.mapper.auth;

import com.onesley.oneclick.dto.auth.CustomRoleDto;
import com.onesley.oneclick.entity.auth.CustomRole;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code CustomRole ↔ CustomRoleDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface CustomRoleMapper {

    CustomRoleDto toDto(CustomRole entity);

    List<CustomRoleDto> toDtoList(List<CustomRole> entities);
}
