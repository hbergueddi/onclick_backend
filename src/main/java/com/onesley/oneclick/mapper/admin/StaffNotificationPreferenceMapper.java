package com.onesley.oneclick.mapper.admin;

import com.onesley.oneclick.dto.admin.StaffNotificationPreferenceDto;
import com.onesley.oneclick.entity.admin.StaffNotificationPreference;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Mapper {@code StaffNotificationPreference ↔ StaffNotificationPreferenceDto} (généré par scripts/scaffold-jpa.mjs).
 * componentModel="spring" est défini globalement via pom.xml compilerArgs.
 */
@Mapper
public interface StaffNotificationPreferenceMapper {

    StaffNotificationPreferenceDto toDto(StaffNotificationPreference entity);

    List<StaffNotificationPreferenceDto> toDtoList(List<StaffNotificationPreference> entities);
}
