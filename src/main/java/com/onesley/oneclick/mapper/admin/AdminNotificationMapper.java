package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.AdminNotificationDto;
import com.onesley.oneclick.entity.admin.AdminNotification;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code AdminNotification ↔ AdminNotificationDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface AdminNotificationMapper {

    AdminNotificationDto toDto(AdminNotification entity);

    List<AdminNotificationDto> toDtoList(List<AdminNotification> entities);
}
