package com.onesley.oneclick.mapper.auth;

import com.onesley.oneclick.dto.auth.StaffRolePermissionDto;
import com.onesley.oneclick.entity.auth.StaffRolePermission;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code StaffRolePermission ↔ StaffRolePermissionDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface StaffRolePermissionMapper {

    StaffRolePermissionDto toDto(StaffRolePermission entity);

    List<StaffRolePermissionDto> toDtoList(List<StaffRolePermission> entities);
}
