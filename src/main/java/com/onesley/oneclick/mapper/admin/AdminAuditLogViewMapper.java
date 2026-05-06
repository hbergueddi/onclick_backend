package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.AdminAuditLogViewDto;
import com.onesley.oneclick.entity.admin.AdminAuditLogView;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code AdminAuditLogView ↔ AdminAuditLogViewDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface AdminAuditLogViewMapper {

    AdminAuditLogViewDto toDto(AdminAuditLogView entity);

    List<AdminAuditLogViewDto> toDtoList(List<AdminAuditLogView> entities);
}
