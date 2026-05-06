package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.NotificationDto;
import com.onesley.oneclick.entity.admin.Notification;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code Notification ↔ NotificationDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface NotificationMapper {

    NotificationDto toDto(Notification entity);

    List<NotificationDto> toDtoList(List<Notification> entities);
}
