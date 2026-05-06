package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.AdminAuditLogDto;
import com.onesley.oneclick.entity.admin.AdminAuditLog;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code AdminAuditLog ↔ AdminAuditLogDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface AdminAuditLogMapper {

    AdminAuditLogDto toDto(AdminAuditLog entity);

    List<AdminAuditLogDto> toDtoList(List<AdminAuditLog> entities);
}
